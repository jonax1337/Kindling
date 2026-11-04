"""Tests of the sweep geometry.   python -I test_sweep.py"""
import os
import random
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import sweep  # noqa: E402


class OneChunkPerStepTest(unittest.TestCase):
    """DESIGN.md 3.1: with the L in place, every step raises exactly the chunk (x + r, z + r) for every radius."""

    def assert_single_increments(self, location: sweep.Location, radius: int) -> None:
        for (x, z), new in zip(location.steps(), sweep.simulate_new_chunks(location, radius)):
            self.assertEqual(new, {(x + radius, z + radius)}, f"radius {radius}, step {(x, z)} of {location}")

    def test_every_radius_on_random_rectangles(self):
        generator = random.Random(1)
        for _ in range(25):
            x0, z0 = generator.randint(-50, 50), generator.randint(-50, 50)
            width, depth = generator.randint(1, 7), generator.randint(1, 7)
            location = sweep.Location("r", "minecraft:overworld", x0, z0, x0 + width - 1, z0 + depth - 1)
            for radius in range(sweep.LARGEST_RADIUS + 1):
                self.assert_single_increments(location, radius)

    def test_without_the_l_the_first_step_raises_a_whole_square(self):
        location = sweep.Location("r", "minecraft:overworld", 0, 0, 3, 3)
        first = sweep.squares_reached([location.steps()[0]], 3)
        self.assertEqual(len(first), 49)


class PredictionTest(unittest.TestCase):
    def setUp(self):
        self.location = sweep.Location("r", "minecraft:overworld", 10, 20, 11, 21)

    def test_raster_order(self):
        self.assertEqual(self.location.steps(), [(10, 20), (11, 20), (10, 21), (11, 21)])

    def test_chain_per_step_in_status_order(self):
        events = sweep.predicted_events(self.location)
        self.assertEqual(len(events), 4 * len(sweep.STATUS_ORDER))
        first_step = [event for event in events if event.step == 0]
        self.assertEqual([event.status for event in first_step], list(sweep.STATUS_ORDER))
        self.assertEqual(first_step[0].chunk, (23, 33))
        self.assertEqual(first_step[-1].chunk, (12, 22))

    def test_bootstrap_is_an_l(self):
        chunks = set(self.location.bootstrap_chunks())
        self.assertEqual(chunks, {(9, 19), (9, 20), (9, 21), (10, 19), (11, 19)})

    def test_must_be_full_covers_the_rectangle_and_two_around(self):
        full = sweep.must_be_full(self.location)
        self.assertEqual(len(full), 6 * 6)
        self.assertIn((8, 18), full)
        self.assertIn((13, 23), full)


if __name__ == "__main__":
    unittest.main()
