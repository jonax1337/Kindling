"""Makes the comparer modules of ../vanilla-diff importable (python -I adds no script directories)."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
VANILLA_DIFF = HERE.parent / "vanilla-diff"
for folder in (HERE, VANILLA_DIFF):
    if str(folder) not in sys.path:
        sys.path.insert(0, str(folder))
