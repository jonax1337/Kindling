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


SLEEPING = check.PAIR_RULES["SLEEPING"]
VILLAGERS = check.PAIR_RULES["VILLAGERS"]
SLEEPING_COUNTERS = {"hopper_chain_items": 1350, "hopper_idle_items": 0, "rewrite_arrived": 80, "rewrite_left": 80,
                     "rewrite_duplicated": 0, "placement_target_arrived": 20, "placement_source_arrived": 20,
                     "gate_brewed_potions": 6, "gate_smelted_items": 10}
VILLAGER_COUNTERS = {"bed_claims": 48, "job_site_claims": 48, "shared_beds": 0, "shared_job_sites": 0}


def option_run(toggle, value, counters, options=None, settled=True):
    return {"settled": settled, "scenario": SCENARIO, "counters": dict(counters),
            "options": {toggle: value, **(options or {})}}


def sleeping(value, **changes):
    return option_run(SLEEPING.toggle, value, {**SLEEPING_COUNTERS, **changes})


def villagers(value, **changes):
    return option_run(VILLAGERS.toggle, value, {**VILLAGER_COUNTERS, **changes})


class PairProblems(unittest.TestCase):
    def test_identical_counters_agree(self):
        self.assertEqual([], check.pair_problems(sleeping(True), sleeping(False), SLEEPING))

    def test_differing_counter_names_its_cause(self):
        problems = check.pair_problems(sleeping(True, gate_smelted_items=0), sleeping(False), SLEEPING)
        self.assertEqual(1, len(problems))
        self.assertIn("gate_smelted_items: option on 0, option off 10 (-10)", problems[0])
        self.assertIn("furnace", problems[0])

    def test_duplicated_items_are_a_problem_on_either_side(self):
        on = sleeping(True, rewrite_arrived=160, rewrite_duplicated=80)
        problems = check.pair_problems(on, sleeping(False), SLEEPING)
        self.assertTrue(any(problem.startswith("rewrite_duplicated is 80 with the option on") for problem in problems))

    def test_scenario_that_did_nothing_is_not_parity(self):
        idle = {name: 0 for name in SLEEPING_COUNTERS}
        problems = check.pair_problems(option_run(SLEEPING.toggle, True, idle),
                                       option_run(SLEEPING.toggle, False, idle), SLEEPING)
        self.assertEqual(len(SLEEPING.positive), len(problems))

    def test_overlay_that_did_not_apply_is_caught(self):
        problems = check.pair_problems(sleeping(True), sleeping(True), SLEEPING)
        self.assertEqual(["option performance.sleeping-block-entity was True in the run that should have it off"],
                         problems)

    def test_other_option_differing_is_caught(self):
        on = option_run(SLEEPING.toggle, True, SLEEPING_COUNTERS, {"spigot.hopper-amount": 3})
        problems = check.pair_problems(on, sleeping(False), SLEEPING)
        self.assertEqual(["option spigot.hopper-amount is 3 on one side and None on the other"], problems)

    def test_unsettled_run_is_rejected(self):
        self.assertEqual(1, len(check.pair_problems(sleeping(True), option_run(SLEEPING.toggle, False, {}, settled=False),
                                                    SLEEPING)))

    def test_villager_claims_may_differ_within_tolerance(self):
        self.assertEqual([], check.pair_problems(villagers(True, bed_claims=45), villagers(False), VILLAGERS))

    def test_villager_claims_beyond_tolerance_differ(self):
        problems = check.pair_problems(villagers(True, bed_claims=30), villagers(False), VILLAGERS)
        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("bed_claims: option on 30, option off 48"))

    def test_shared_villager_claims_never_pass(self):
        problems = check.pair_problems(villagers(True, shared_beds=2), villagers(False), VILLAGERS)
        self.assertTrue(any(problem.startswith("shared_beds is 2 with the option on") for problem in problems))
        self.assertTrue(any(problem.startswith("shared_beds: option on 2, option off 0") for problem in problems))

    def test_counter_missing_on_one_side(self):
        on = villagers(True)
        del on["counters"]["job_site_claims"]
        problems = check.pair_problems(on, villagers(False), VILLAGERS)
        self.assertIn("job_site_claims: only the option-off run has this counter", problems)


class PairSet(unittest.TestCase):
    def test_runs_pair_up_one_to_one(self):
        self.assertEqual([], check.pair_set_problems([{}], [{}]))

    def test_unequal_numbers_of_runs_are_refused(self):
        self.assertEqual(1, len(check.pair_set_problems([{}, {}], [{}])))

    def test_empty_file_is_refused(self):
        self.assertEqual(1, len(check.pair_set_problems([], [{}])))


if __name__ == "__main__":
    unittest.main()
