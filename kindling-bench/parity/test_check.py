"""Guards the comparison and --update rules of check.py: python -m unittest test_check (from this folder)."""

import unittest

import check

SCENARIO = {"players": 1, "ticks": 100}
GOLDEN = {"scenario": SCENARIO, "counters": {"a": 1, "b": 2}}


def run(counters=None, settled=True, scenario=None):
    return {"attempt": 1, "settled": settled, "scenario": scenario or SCENARIO, "counters": counters or {"a": 1, "b": 2}}


class ProblemsOf(unittest.TestCase):
    def test_identical_run_has_no_problems(self):
        self.assertEqual([], check.problems_of(run(), GOLDEN))

    def test_differing_counter_is_named(self):
        self.assertEqual(["b: expected 2, got 3 (+1)"], check.problems_of(run({"a": 1, "b": 3}), GOLDEN))

    def test_missing_and_extra_counters(self):
        problems = check.problems_of(run({"a": 1, "c": 5}), GOLDEN)
        self.assertEqual(2, len(problems))
        self.assertIn("b: expected 2, the run has no such counter", problems)
        self.assertIn("c: the run reports 5, the golden file has no such counter", problems)

    def test_unsettled_run_is_rejected(self):
        self.assertEqual(1, len(check.problems_of(run(settled=False), GOLDEN)))

    def test_scenario_mismatch_lists_only_differing_fields(self):
        problems = check.problems_of(run(scenario={"players": 2, "ticks": 100}), GOLDEN)
        self.assertEqual(2, len(problems))
        self.assertIn("players", problems[1])
        self.assertNotIn("ticks", problems[1])


class UnfitForGolden(unittest.TestCase):
    def test_agreeing_runs_are_fit(self):
        self.assertEqual([], check.unfit_for_golden(run(), run()))

    def test_disagreeing_runs_are_refused(self):
        self.assertNotEqual([], check.unfit_for_golden(run({"a": 9, "b": 2}), run()))

    def test_unsettled_run_is_refused(self):
        self.assertNotEqual([], check.unfit_for_golden(run(settled=False), run()))


if __name__ == "__main__":
    unittest.main()
