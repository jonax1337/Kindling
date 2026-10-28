"""Download Mojang's official server jar through the version manifest and verify its sha1.

    python -I fetch_vanilla.py [--version 26.3] [--out jars]

Prints the path of the verified jar on the last line. An existing jar with the right
sha1 is kept, so running this twice downloads once.
"""
import argparse
import hashlib
import json
import sys
import urllib.request
from pathlib import Path

MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
DEFAULT_VERSION = "26.3"
CHUNK_BYTES = 1 << 20
TIMEOUT_SECONDS = 60


def fetch_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=TIMEOUT_SECONDS) as response:
        return json.load(response)


def server_download(version: str) -> dict:
    manifest = fetch_json(MANIFEST_URL)
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        raise SystemExit(f"version {version} is not in the Mojang version manifest")
    return fetch_json(entry["url"])["downloads"]["server"]


def sha1_of(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(CHUNK_BYTES), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, target: Path) -> None:
    with urllib.request.urlopen(url, timeout=TIMEOUT_SECONDS) as response, target.open("wb") as out:
        for block in iter(lambda: response.read(CHUNK_BYTES), b""):
            out.write(block)


def fetch_verified_jar(version: str, out_dir: Path) -> Path:
    download_info = server_download(version)
    target = out_dir / f"minecraft-server-{version}.jar"
    if target.is_file() and sha1_of(target) == download_info["sha1"]:
        return target
    out_dir.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix(".jar.part")
    download(download_info["url"], partial)
    actual = sha1_of(partial)
    if actual != download_info["sha1"]:
        partial.unlink()
        raise SystemExit(f"sha1 mismatch for {version}: expected {download_info['sha1']}, got {actual}")
    partial.replace(target)
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", default=DEFAULT_VERSION)
    parser.add_argument("--out", type=Path, default=Path(__file__).resolve().parent / "jars")
    args = parser.parse_args()
    jar = fetch_verified_jar(args.version, args.out)
    print(f"sha1 verified: {sha1_of(jar)}", file=sys.stderr)
    print(jar)


if __name__ == "__main__":
    main()
