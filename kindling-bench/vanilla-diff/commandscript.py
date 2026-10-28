"""The file of console commands that run.sh feeds to a server.

One command per line. Lines starting with # are skipped. Two lines are directives that run.sh executes itself:
    @await-loaded <min chunk x> <min chunk z> <max chunk x> <max chunk z>   wait until those chunks are loaded
    @step <ticks>                                                         tick step, then wait until they ran
"""
AWAIT_LOADED = "@await-loaded"
STEP = "@step"


class CommandScript:
    def __init__(self):
        self._lines: list[str] = []

    def comment(self, text: str) -> None:
        self._lines.append(f"# {text}")

    def command(self, text: str) -> None:
        self._lines.append(text)

    def commands(self, texts) -> None:
        self._lines.extend(texts)

    def await_loaded(self, min_x: int, min_z: int, max_x: int, max_z: int) -> None:
        self._lines.append(f"{AWAIT_LOADED} {min_x} {min_z} {max_x} {max_z}")

    def step(self, ticks: int) -> None:
        self._lines.append(f"{STEP} {ticks}")

    def text(self) -> str:
        return "\n".join(self._lines) + "\n"
