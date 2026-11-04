"""Starts one server jar in a folder and talks to its console.

Every server this tool starts listens on 127.0.0.1:25999 and nowhere else (AGENTS.md); the properties are written
here and checked again in the log. Mojang's jar and the Paper family (Paper, Leaf, Kindling) are told apart by the
jar's main class, and each family gets its single-worker-thread setup.
"""
import hashlib
import re
import socket
import subprocess
import threading
import time
import zipfile
from pathlib import Path

ADDRESS = "127.0.0.1"
PORT = 25999
SEED = "-4172144997902289642"
MOJANG, PAPER_FAMILY = "mojang", "paper"
_MAIN_CLASSES = {"net.minecraft.bundler.Main": MOJANG, "io.papermc.paperclip.Main": PAPER_FAMILY}
_BRANDS = ("Paper", "Leaf", "Kindling", "Gale", "Purpur")  # -D<brand>.WorkerThreadCount, whichever the jar reads
_ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")
START_TIMEOUT = 300
ANSWER_TIMEOUT = 60
STOP_TIMEOUT = 180
HERE = Path(__file__).resolve().parent
JFR_SETTINGS = HERE / "chunkgen.jfc"
JFR_FILE = "parity.jfr"

BASE_PROPERTIES = {
    "server-ip": ADDRESS, "server-port": str(PORT), "online-mode": "false", "level-name": "world",
    "level-seed": SEED, "level-type": "minecraft:normal", "generate-structures": "true",
    "view-distance": "4", "simulation-distance": "4", "spawn-protection": "0", "max-players": "1",
    "pause-when-empty-seconds": "-1", "max-tick-time": "-1", "sync-chunk-writes": "true",
    "region-file-compression": "deflate", "enable-rcon": "false", "enable-query": "false", "enable-status": "false",
}


class ServerError(RuntimeError):
    pass


def jar_family(jar: Path) -> str:
    with zipfile.ZipFile(jar) as archive:
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
    match = re.search(r"^Main-Class:\s*(\S+)", manifest, re.MULTILINE)
    family = _MAIN_CLASSES.get(match.group(1) if match else "")
    if family is None:
        raise ServerError(f"{jar.name}: unknown main class {match.group(1) if match else 'none'}")
    return family


def paperclip_mojang_jar(jar: Path) -> tuple[str, str]:
    """(sha256, file name) of the Mojang jar a Paperclip jar patches, from META-INF/download-context."""
    with zipfile.ZipFile(jar) as archive:
        sha256, _url, name = archive.read("META-INF/download-context").decode("utf-8").strip().split("\t")
    return sha256.lower(), name


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def write_server_files(folder: Path, jar: Path, mojang_jar: Path, properties: dict[str, str]) -> None:
    """eula, server.properties and, for Paperclip, the verified Mojang jar in its cache (so nothing is downloaded)."""
    merged = {**BASE_PROPERTIES, **properties}
    if merged["server-ip"] != ADDRESS or merged["server-port"] != str(PORT):
        raise ServerError(f"refusing to listen on {merged['server-ip']}:{merged['server-port']}")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    text = "".join(f"{key}={value}\n" for key, value in merged.items())
    (folder / "server.properties").write_text(text, encoding="utf-8", newline="\n")
    if jar_family(jar) == PAPER_FAMILY:
        _place_paperclip_cache(folder, jar, mojang_jar)


def _place_paperclip_cache(folder: Path, jar: Path, mojang_jar: Path) -> None:
    expected, name = paperclip_mojang_jar(jar)
    actual = sha256_of(mojang_jar)
    if actual != expected:
        raise ServerError(f"{jar.name} patches a Mojang jar with sha256 {expected}, {mojang_jar.name} is {actual}")
    cache = folder / "cache"
    cache.mkdir(exist_ok=True)
    (cache / name).write_bytes(mojang_jar.read_bytes())


def single_worker_arguments(family: str) -> list[str]:
    """One generation thread. For the Paper family also: import the vanilla bootstrap without waiting (Paper's
    VanillaWorldMigration copies level-wide saved data next to the dimension and adds Paper's own metadata; it does not
    touch region files; Leaf turns Paper's 30 s delay into a 'confirm' prompt, which this property skips too)."""
    if family == MOJANG:
        return ["-Dmax.bg.threads=1"]
    return [f"-D{brand}.WorkerThreadCount=1" for brand in _BRANDS] + ["-Dpaper.disableMigrationDelay=true"]


def jfr_arguments() -> list[str]:
    """A recording from JVM start that enables only minecraft.ChunkGeneration (disabled by default and not in
    Mojang's own flightrecorder-config.jfc, so `/jfr start` would not record it). The file lands in the server
    folder, which is the working directory."""
    return [f"-XX:StartFlightRecording=name=parity,settings={JFR_SETTINGS},"
            f"filename={JFR_FILE},dumponexit=true"]


_MUST_STAY = ("server-ip", "server-port", "enable-rcon", "enable-query", "enable-status")


def check_properties_on_disk(folder: Path) -> None:
    """Read server.properties as the server will read it, just before it starts: an overlay or an edit must not have
    moved the bind address (the log is only read after the server listens)."""
    path = folder / "server.properties"
    found = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.lstrip().startswith("#"):
            found[key.strip()] = value.strip()
    wrong = {key: found.get(key) for key in _MUST_STAY if found.get(key) != BASE_PROPERTIES[key]}
    if wrong:
        raise ServerError(f"{path}: refusing to start, these must be {{{', '.join(f'{k}={BASE_PROPERTIES[k]}' for k in wrong)}}}"
                          f" but are {wrong}")


def ensure_port_free() -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind((ADDRESS, PORT))
        except OSError as error:
            raise ServerError(f"{ADDRESS}:{PORT} is in use; another server must not be running") from error


class Server:
    """One running server. Lines of its console are kept in order; answers are matched after the command was sent."""

    def __init__(self, java: Path, jar: Path, folder: Path, heap: str, jvm_arguments: list[str]):
        self.java, self.jar, self.folder, self.heap = java, jar, folder, heap
        self.jvm_arguments = jvm_arguments
        self.lines: list[str] = []
        self._changed = threading.Condition()
        self._process: subprocess.Popen | None = None

    def start(self) -> None:
        ensure_port_free()
        check_properties_on_disk(self.folder)
        command = [str(self.java), f"-Xmx{self.heap}", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                   "-Dstderr.encoding=UTF-8", *self.jvm_arguments, "-jar", str(self.jar), "--nogui"]
        (self.folder / "command-line.txt").write_text(" ".join(command) + "\n", encoding="utf-8")
        self._process = subprocess.Popen(command, cwd=self.folder, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, encoding="utf-8", errors="replace")
        threading.Thread(target=self._read_console, daemon=True).start()
        try:
            self.wait_for(r"Done \([0-9.,]+s\)!", START_TIMEOUT, 0)
            self._check_address()
        except ServerError:
            self.kill()
            raise

    def _read_console(self) -> None:
        with (self.folder / "console.log").open("w", encoding="utf-8") as log:
            for raw in self._process.stdout:
                line = _ANSI.sub("", raw.rstrip("\r\n"))
                log.write(line + "\n")
                log.flush()
                with self._changed:
                    self.lines.append(line)
                    self._changed.notify_all()
        with self._changed:
            self._changed.notify_all()

    def _check_address(self) -> None:
        bound = [line for line in self.lines if "Starting Minecraft server on" in line]
        if not bound or f"{ADDRESS}:{PORT}" not in bound[-1]:
            raise ServerError(f"the server did not report {ADDRESS}:{PORT}: {bound}")

    def wait_for(self, pattern: str, timeout: float, after: int) -> re.Match:
        """The first console line at index >= after that matches, waiting up to timeout seconds."""
        compiled = re.compile(pattern)
        deadline = time.monotonic() + timeout
        position = after
        with self._changed:
            while True:
                for index in range(position, len(self.lines)):
                    match = compiled.search(self.lines[index])
                    if match:
                        return match
                position = len(self.lines)
                if self._process.poll() is not None:
                    raise ServerError(f"the server exited while waiting for /{pattern}/")
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise ServerError(f"timed out after {timeout} s waiting for /{pattern}/")
                self._changed.wait(min(remaining, 1.0))

    def command(self, text: str, answer: str, timeout: float = ANSWER_TIMEOUT) -> re.Match:
        mark = self.send(text)
        return self.wait_for(answer, timeout, mark)

    def send(self, text: str) -> int:
        """Sends one console line; returns the line index after which its answer will appear."""
        with self._changed:
            mark = len(self.lines)
        self._process.stdin.write(text + "\n")
        self._process.stdin.flush()
        return mark

    def stop(self) -> int:
        self.send("stop")
        try:
            return self._process.wait(STOP_TIMEOUT)
        except subprocess.TimeoutExpired:
            self.kill()
            raise ServerError(f"the server did not stop within {STOP_TIMEOUT} s")

    def kill(self) -> None:
        if self._process and self._process.poll() is None:
            self._process.kill()
            self._process.wait(30)
