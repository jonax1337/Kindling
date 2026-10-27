"""Checks that a CI bases run did exactly what the golden file says: every contraption counter identical.

The counters of the bases scenario count what each contraption did in a fixed number of server ticks, so they do not
depend on how fast the machine is. A counter that differs means block entity or redstone behaviour changed. Exit code 1
and one line per difference when the run does not match.

    python -I check.py <runs/parity-ci.json>                  # compare every run in the file with bases-ci.json
    python -I check.py <runs/parity-ci.json> --update         # rewrite the golden file from these runs (see README)

The sleeping and villagers scenarios have no golden file: each runs twice, with the option under test on and off, and
the two must agree (PAIR_RULES says what agreeing means per scenario). Run number n of the first file is compared with
run number n of the second.

    python -I check.py --pair <runs/sleeping-on.json> <runs/sleeping-off.json>
"""

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

GOLDEN = Path(__file__).resolve().parent / "bases-ci.json"


@dataclass(frozen=True)
class PairRule:
    """What the option-on run of a scenario has to show next to the option-off run.

    toggle: the server option under test; the first run must report it true, the second false, and every other option
        the runs report must be equal.
    zero: counters that are 0 in both runs, whatever the option.
    positive: counters above 0 in the reference run (option off); without that, a scenario that built nothing would
        agree with itself.
    tolerance: counter -> the share of the larger of the two values the runs may differ by. Anything not listed must
        be identical.
    """

    toggle: str
    zero: tuple[str, ...] = ()
    positive: tuple[str, ...] = ()
    tolerance: dict[str, float] = field(default_factory=dict)


# The claims of the villagers scenario cannot be compared exactly. Every hall has as many beds and job sites as it has
# villagers, so both runs end with every villager holding one of each, but when the last ones get there depends on the
# random retry delay of the brain (20 to 40 ticks per attempt) and on how long a path takes to arrive, and async paths
# arrive later. A run that ends before the slowest villager is done is a few claims short; 10 % covers that and still
# catches a run that lost whole halls. Sharing has no such slack: it is 0, or it is the bug.
PAIR_RULES = {
    "SLEEPING": PairRule(
        toggle="performance.sleeping-block-entity",
        zero=("rewrite_duplicated",),
        positive=("hopper_chain_items", "rewrite_arrived", "rewrite_left", "placement_target_arrived",
                  "placement_source_arrived", "gate_brewed_potions", "gate_smelted_items"),
    ),
    "VILLAGERS": PairRule(
        toggle="async.async-pathfinding.enabled",
        zero=("shared_beds", "shared_job_sites"),
        positive=("bed_claims", "job_site_claims"),
        tolerance={"bed_claims": 0.10, "job_site_claims": 0.10},
    ),
}

HOPPER_RATES = "spigot.yml hopper-amount and ticks-per.hopper-transfer are ignored by the sleeping hopper path"
HOPPER_EVENT = "InventoryMoveItemEvent#setItem on a hopper push (the sleeping hopper path takes the item from a copy)"
POI_TAKE = "AcquirePoi ignores the result of PoiManager#take, so a late async path claims a place that is taken"

# What a counter that differs points at, printed behind the difference.
HINTS = {
    "rewrite_duplicated": HOPPER_EVENT + ": items made from nothing",
    "rewrite_arrived": HOPPER_EVENT,
    "rewrite_left": HOPPER_EVENT,
    "placement_target_arrived": "a chest placed without physics under a sleeping hopper is never filled",
    "placement_source_arrived": "a chest placed without physics over a sleeping hopper is never emptied",
    "hopper_chain_items": HOPPER_RATES,
    "hopper_idle_items": HOPPER_RATES,
    "hopper_stuck_items": HOPPER_RATES,
    "gate_brewed_potions": "a brewing stand whose fuel a plugin cancelled for a while stays asleep",
    "gate_smelted_items": "a furnace that a plugin kept from burning for a while stays asleep",
    "shared_beds": "two villagers claim the same bed: " + POI_TAKE,
    "shared_job_sites": "two villagers claim the same job site: " + POI_TAKE,
}


def main() -> int:
    args = parse_arguments()
    if args.pair:
        return check_pair(*args.pair)
    runs = json.loads(args.result.read_text(encoding="utf-8"))
    if not runs:
        print(f"no runs in {args.result}")
        return 1
    if args.update:
        return update_golden(args.golden, runs)
    golden = json.loads(args.golden.read_text(encoding="utf-8"))
    problems = [f"run {run.get('attempt')}: {problem}" for run in runs for problem in problems_of(run, golden)]
    if problems:
        print(f"PARITY FAILED against {args.golden.name}:")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    print(f"parity ok: {len(runs)} run(s), {len(golden['counters'])} counters identical to {args.golden.name}")
    return 0


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("result", type=Path, nargs="?", help="the runs/<label>.json that run_bench.py wrote")
    parser.add_argument("--golden", type=Path, default=GOLDEN)
    parser.add_argument("--update", action="store_true", help="write the golden file from these runs")
    parser.add_argument("--pair", type=Path, nargs=2, metavar=("ON", "OFF"),
                        help="compare a sleeping or villagers run with its option on to one with it off")
    args = parser.parse_args()
    if (args.result is None) == (args.pair is None):
        parser.error("give either a result file or --pair ON OFF")
    if args.pair and args.update:
        parser.error("--update only writes the golden file of the bases scenario")
    return args


def problems_of(run: dict, golden: dict) -> list[str]:
    """Everything that makes this run not comparable with the golden file, or different from it."""
    if run.get("settled") is not True:
        return ["built its bases before world generation settled, so its counters mean nothing"]
    if run.get("scenario") != golden["scenario"]:
        differences = counter_differences(golden["scenario"], run.get("scenario", {}))
        return ["ran another scenario than the golden file"] + [f"  scenario {line}" for line in differences]
    return counter_differences(golden["counters"], run.get("counters", {}))


def counter_differences(expected: dict[str, int], actual: dict[str, int]) -> list[str]:
    lines = []
    for name in sorted(expected.keys() | actual.keys()):
        if name not in actual:
            lines.append(f"{name}: expected {expected[name]}, the run has no such counter")
        elif name not in expected:
            lines.append(f"{name}: the run reports {actual[name]}, the golden file has no such counter")
        elif expected[name] != actual[name]:
            lines.append(f"{name}: expected {expected[name]}, got {actual[name]} ({actual[name] - expected[name]:+d})")
    return lines


def check_pair(on_file: Path, off_file: Path) -> int:
    """Compares run n of the option-on file with run n of the option-off file; exit code 1 on any problem."""
    on_runs, off_runs = load_runs(on_file), load_runs(off_file)
    problems = pair_set_problems(on_runs, off_runs)
    scenario = off_runs[0].get("scenario", {}).get("type") if off_runs else None
    rule = PAIR_RULES.get(scenario)
    if rule is None and not problems:
        problems = [f"no pair rule for scenario type {scenario}, only for {', '.join(sorted(PAIR_RULES))}"]
    if not problems:
        problems = [f"run {number}: {problem}" for number, (on, off) in enumerate(zip(on_runs, off_runs), 1)
                    for problem in pair_problems(on, off, rule)]
    if problems:
        print(f"PARITY FAILED: {scenario} with the option on ({on_file.name}) against off ({off_file.name}):")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    print(f"parity ok: {scenario}, {len(on_runs)} run(s), {len(off_runs[0]['counters'])} counters agree with "
          f"{rule.toggle} on and off")
    return 0


def load_runs(path: Path) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8"))


def pair_set_problems(on_runs: list[dict], off_runs: list[dict]) -> list[str]:
    if not on_runs or not off_runs:
        return ["a result file holds no runs"]
    if len(on_runs) != len(off_runs):
        return [f"{len(on_runs)} run(s) with the option on against {len(off_runs)} with it off"]
    return []


def pair_problems(on: dict, off: dict, rule: PairRule) -> list[str]:
    """Everything that makes the option-on run not comparable with the option-off run, or different from it."""
    if on.get("settled") is not True or off.get("settled") is not True:
        return ["built before world generation settled, so its counters mean nothing"]
    if on.get("scenario") != off.get("scenario"):
        differences = counter_differences(off.get("scenario", {}), on.get("scenario", {}))
        return ["the two runs ran different scenarios"] + [f"  scenario {line}" for line in differences]
    option_problems = pair_option_problems(on.get("options", {}), off.get("options", {}), rule.toggle)
    if option_problems:
        return option_problems
    on_counters, off_counters = on.get("counters", {}), off.get("counters", {})
    problems = counter_rule_problems(on_counters, off_counters, rule)
    for name in sorted(on_counters.keys() | off_counters.keys()):
        problems += pair_counter_problems(name, on_counters, off_counters, rule)
    return problems


def pair_option_problems(on: dict, off: dict, toggle: str) -> list[str]:
    """The overlays must really have been applied: the toggle differs the right way and nothing else differs."""
    problems = []
    if on.get(toggle) is not True:
        problems.append(f"option {toggle} was {on.get(toggle)} in the run that should have it on")
    if off.get(toggle) is not False:
        problems.append(f"option {toggle} was {off.get(toggle)} in the run that should have it off")
    for name in sorted(on.keys() | off.keys()):
        if name != toggle and on.get(name) != off.get(name):
            problems.append(f"option {name} is {on.get(name)} on one side and {off.get(name)} on the other")
    return problems


def counter_rule_problems(on: dict[str, int], off: dict[str, int], rule: PairRule) -> list[str]:
    """Counters that must be 0 in both runs, and counters that must show that the scenario did something."""
    problems = []
    for name in rule.zero:
        for side, counters in (("on", on), ("off", off)):
            if counters.get(name) != 0:
                problems.append(f"{name} is {counters.get(name)} with the option {side}, it must be 0{hint_of(name)}")
    for name in rule.positive:
        if not off.get(name, 0) > 0:
            problems.append(f"{name} is {off.get(name)} with the option off: the scenario did not do its work")
    return problems


def pair_counter_problems(name: str, on: dict[str, int], off: dict[str, int], rule: PairRule) -> list[str]:
    if name not in on or name not in off:
        return [f"{name}: only the {'option-on' if name in on else 'option-off'} run has this counter"]
    if abs(on[name] - off[name]) <= rule.tolerance.get(name, 0.0) * max(on[name], off[name]):
        return []
    return [f"{name}: option on {on[name]}, option off {off[name]} ({on[name] - off[name]:+d}){hint_of(name)}"]


def hint_of(name: str) -> str:
    return f" -- {HINTS[name]}" if name in HINTS else ""


def update_golden(golden_file: Path, runs: list[dict]) -> int:
    """Writes the golden file, but only from runs that settled and agree with each other."""
    first = runs[0]
    problems = [f"run {run.get('attempt')}: {problem}" for run in runs for problem in unfit_for_golden(run, first)]
    if problems:
        print("not writing the golden file:")
        print("\n".join(f"  {problem}" for problem in problems))
        return 1
    golden = {"scenario": first["scenario"], "counters": first["counters"]}
    golden_file.write_text(json.dumps(golden, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {golden_file} from {len(runs)} run(s)")
    return 0


def unfit_for_golden(run: dict, first: dict) -> list[str]:
    if run.get("settled") is not True:
        return ["built its bases before world generation settled"]
    if run["scenario"] != first["scenario"]:
        return ["ran another scenario than run 1"]
    return counter_differences(first["counters"], run["counters"])


if __name__ == "__main__":
    sys.exit(main())
