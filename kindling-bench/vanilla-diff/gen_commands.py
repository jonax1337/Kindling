"""Write the files that run.sh needs for one mode.

    python -I gen_commands.py <contraptions|worldgen> <output folder>

commands.txt        the console commands (see commandscript.py)
areas.json          the chunk rectangles that the comparer looks at
mode.properties     the server.properties lines that belong to the mode
"""
import argparse
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import contraptions  # noqa: E402
import worldgen_script  # noqa: E402
import worlddiff  # noqa: E402

MODES = {
    "contraptions": (contraptions.build_script, contraptions.SERVER_PROPERTIES),
    "worldgen": (worldgen_script.build_script, worldgen_script.SERVER_PROPERTIES),
}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=sorted(MODES))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build_script, properties = MODES[args.mode]
    script, boxes = build_script()
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "commands.txt").write_text(script.text(), encoding="utf-8", newline="\n")
    (args.output / "areas.json").write_text(json.dumps({worlddiff.OVERWORLD: boxes}), encoding="utf-8")
    (args.output / "mode.properties").write_text(
        "".join(f"{key}={value}\n" for key, value in properties.items()), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
