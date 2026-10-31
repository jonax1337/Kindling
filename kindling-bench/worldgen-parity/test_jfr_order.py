"""Tests of the order check on synthetic JFR event lists.   python -I test_jfr_order.py"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import jfr_order  # noqa: E402
import sweep  # noqa: E402

LOCATION = sweep.Location("t", "minecraft:overworld", 0, 0, 1, 1)
WORKER, MAIN = "Worker-Main-1", "Server thread"
STEP_GAP_NS = 1_000_000


def canonical_events(location=LOCATION, statuses=sweep.STATUS_ORDER) -> list[jfr_order.GenerationEvent]:
    events, clock = [], 10_000
    for predicted in sweep.predicted_events(location):
        if predicted.status not in statuses:
            continue
        thread = MAIN if predicted.status == "full" else WORKER
        events.append(jfr_order.GenerationEvent(clock, 10, thread, location.dimension, predicted.status, predicted.chunk))
        clock += 100 if predicted.status != "full" else STEP_GAP_NS
    return events


def check(events) -> jfr_order.OrderReport:
    return jfr_order.check_order(sorted(events), LOCATION, sweep_start_ns=0)


class OrderCheckTest(unittest.TestCase):
    def test_accepts_the_canonical_sequence(self):
        report = check(canonical_events())
        self.assertTrue(report.ok, report.problems)
        self.assertEqual(report.events_near, 4 * len(sweep.STATUS_ORDER))

    def test_accepts_a_family_that_records_no_light_and_full(self):
        statuses = [s for s in sweep.STATUS_ORDER if s not in ("light", "full")]
        report = check(canonical_events(statuses=statuses))
        self.assertTrue(report.ok, report.problems)
        self.assertNotIn("full", report.recorded_statuses)

    def test_rejects_two_swapped_events(self):
        events = canonical_events()
        events[3], events[4] = events[3]._replace(start_ns=events[4].start_ns), events[4]._replace(start_ns=events[3].start_ns)
        self.assertFalse(check(events).ok)

    def test_rejects_an_extra_event(self):
        events = canonical_events()
        extra = events[5]._replace(chunk=(7, 7), start_ns=events[5].start_ns + 1)
        self.assertFalse(check(events + [extra]).ok)

    def test_rejects_a_missing_event(self):
        events = canonical_events()
        self.assertFalse(check(events[:10] + events[11:]).ok)

    def test_rejects_a_second_worker_thread(self):
        events = canonical_events()
        events[2] = events[2]._replace(thread="Worker-Main-2")
        report = check(events)
        self.assertFalse(report.ok)
        self.assertIn("threads", " ".join(report.problems))

    def test_rejects_steps_that_overlap(self):
        events = canonical_events()
        last_of_first_step = len(sweep.STATUS_ORDER) - 1
        events[last_of_first_step] = events[last_of_first_step]._replace(duration_ns=10 * STEP_GAP_NS)
        report = check(events)
        self.assertFalse(report.ok)
        self.assertIn("before step 0 had finished", " ".join(report.problems))

    def test_rejects_generation_elsewhere_during_the_sweep(self):
        stray = jfr_order.GenerationEvent(20_000, 10, WORKER, "minecraft:the_nether", "features", (0, 0))
        self.assertFalse(check(canonical_events() + [stray]).ok)

    def test_allows_generation_elsewhere_before_the_sweep(self):
        startup = jfr_order.GenerationEvent(-5, 10, WORKER, "minecraft:overworld", "full", (500, 500))
        report = jfr_order.check_order(sorted(canonical_events() + [startup]), LOCATION, sweep_start_ns=0)
        self.assertTrue(report.ok, report.problems)
        self.assertEqual(report.events_elsewhere_before, 1)

    def test_requires_the_core_statuses(self):
        events = [event for event in canonical_events() if event.status != "features"]
        self.assertIn("no features event", " ".join(check(events).problems))


class ParsingTest(unittest.TestCase):
    def test_instant_with_nanoseconds_and_offset(self):
        a = jfr_order.parse_instant_ns("2026-10-30T13:39:48.264225900+01:00")
        b = jfr_order.parse_instant_ns("2026-10-30T12:39:48.264225901+00:00")
        self.assertEqual(b - a, 1)

    def test_durations(self):
        self.assertEqual(jfr_order.parse_duration_ns("PT0.0016196S"), 1_619_600)
        self.assertEqual(jfr_order.parse_duration_ns("PT1M2.5S"), 62_500_000_000)
        self.assertEqual(jfr_order.parse_duration_ns("PT0S"), 0)

    def test_event_document(self):
        document = {"recording": {"events": [{"type": jfr_order.EVENT_NAME, "values": {
            "startTime": "2026-10-30T13:39:48.000000001+01:00", "duration": "PT0.000001S",
            "eventThread": {"javaName": WORKER}, "chunkPosX": 3, "chunkPosZ": -4,
            "status": "minecraft:features", "level": "minecraft:overworld"}}]}}
        (event,) = jfr_order.parse_events(document)
        self.assertEqual((event.status, event.chunk, event.thread, event.duration_ns), ("features", (3, -4), WORKER, 1000))


if __name__ == "__main__":
    unittest.main()
