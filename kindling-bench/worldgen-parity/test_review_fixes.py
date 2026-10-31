"""Tests of what the review of the first version found: the promotion-race footprint, differences no category
explains, comparing the right runs, the java and jfr lookup, and the bind address.   python -I test_review_fixes.py"""
import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import paths  # noqa: E402,F401
import compare_all  # noqa: E402
import mutate_region  # noqa: E402
import parity  # noqa: E402
import runinfo  # noqa: E402
import runs  # noqa: E402
import server  # noqa: E402
import sweep  # noqa: E402

LOCATION = sweep.Location("t", "minecraft:overworld", 0, 0, 1, 1)


def block_states(*, stone_at=()) -> dict:
    indices = [1 if cell in stone_at else 0 for cell in range(4096)]
    if not stone_at:
        return {"palette": ["minecraft:air"]}
    return {"palette": ["minecraft:air", "minecraft:stone"], "data": mutate_region.pack_indices(indices, 4)}


def section(y: int, *, stone_at=(), biome="minecraft:plains", **fields) -> dict:
    return {"Y": y, "block_states": block_states(stone_at=stone_at), "biomes": {"palette": [biome]}, **fields}


def chunk(*sections: dict, **fields) -> dict:
    return {"Status": "minecraft:full", "sections": list(sections), **fields}


def categories(a: dict, b: dict, **keywords) -> list[str]:
    return [category for category, _ in compare_all.diff_chunk((0, 0), a, b, **keywords)]


class FootprintTest(unittest.TestCase):
    """A chunk Mojang's promotion left unprocessed is compared except what post-processing can change."""

    def test_a_block_outside_the_footprint_is_found_inside_it_is_masked(self):
        # section Y=-4, cell index = x + 16 z + 256 y; the mark is at local (1, 2, 3), the planted blocks at cell 0
        # (outside) and at the mark (inside)
        mark = 1 | 2 << 4 | 3 << 8
        footprint = compare_all.post_processing_footprint((0, 0), chunk(section(-4), PostProcessing=[[mark]]))
        inside = 1 + 16 * 3 + 256 * 2
        a = chunk(section(-4))
        for stone_at, expect_found in (((0,), True), ((inside,), False)):
            b = chunk(section(-4, stone_at=stone_at), PostProcessing=[[]])
            found = list(compare_all.diff_chunk((0, 0), a, b, excluded=True, footprint=footprint))
            reported = [(c, t) for c, t in found if not footprint.covers(c, t)]
            self.assertEqual(bool(reported), expect_found, found)

    def test_post_processing_lists_are_not_a_difference_in_an_excluded_chunk(self):
        a = chunk(section(-4), PostProcessing=[[5]])
        b = chunk(section(-4), PostProcessing=[[]])
        self.assertEqual(categories(a, b, excluded=True), [])
        self.assertEqual(categories(a, b), ["post-processing"])

    def test_a_scheduled_tick_position_is_read_from_the_description(self):
        footprint = compare_all.Footprint({(1, 2, 3)})
        tick = '{"i":"minecraft:water","p":0,"t":5,"x":1,"y":2,"z":3}'
        self.assertTrue(footprint.covers("scheduled tick", f"fluid tick only in B: {tick}"))
        self.assertFalse(footprint.covers("scheduled tick", f"fluid tick only in B: {tick.replace('x\":1', 'x\":9')}"))
        self.assertFalse(footprint.covers("scheduled tick", "something unparseable"))

    def test_heightmap_columns_under_the_footprint_are_counted_apart(self):
        footprint = compare_all.Footprint({(1, 64, 0)})
        values = [64] * 256
        changed = list(values)
        changed[1] = 70  # column x=1, z=0: under the footprint
        a = {"WORLD_SURFACE": mutate_region.pack_indices(values, 9)}
        b = {"WORLD_SURFACE": mutate_region.pack_indices(changed, 9)}
        found = list(compare_all._diff_heightmaps((0, 0), a, b, footprint))
        self.assertEqual([category for category, _ in found], ["masked heightmap"])
        changed[200] = 70
        b = {"WORLD_SURFACE": mutate_region.pack_indices(changed, 9)}
        self.assertEqual([c for c, _ in compare_all._diff_heightmaps((0, 0), a, b, footprint)],
                         ["heightmap", "masked heightmap"])


class UnexplainedTest(unittest.TestCase):
    """Differences that the category functions look away from must not pass."""

    def test_a_biome_container_that_one_side_lacks(self):
        a = chunk(section(-4))
        b = chunk({k: v for k, v in section(-4).items() if k != "biomes"})
        self.assertEqual(categories(a, b), ["biome"])

    def test_a_dropped_section_with_content(self):
        self.assertEqual(categories(chunk(section(-4), section(19)), chunk(section(-4))), ["section"])

    def test_a_section_that_only_carries_light_is_not_a_difference(self):
        light_only = {"Y": -5, "BlockLight": (0,) * 4, "starlight.blocklight_state": 1}
        self.assertEqual(categories(chunk(section(-4)), chunk(section(-4), light_only)), [])

    def test_a_second_block_entity_at_a_position_is_not_hidden(self):
        entity = {"id": "minecraft:chest", "x": 1, "y": 2, "z": 3}
        a = chunk(section(-4), block_entities=[entity])
        b = chunk(section(-4), block_entities=[{**entity, "id": "minecraft:planted"}, entity])
        self.assertIn("block entity", categories(a, b))

    def test_the_same_blocks_in_another_palette_order_are_equal(self):
        stone = {"palette": ["minecraft:stone", "minecraft:air"], "data": mutate_region.pack_indices(
            [0 if cell == 5 else 1 for cell in range(4096)], 4)}
        a = chunk({"Y": -4, "block_states": block_states(stone_at=(5,)), "biomes": {"palette": ["minecraft:plains"]}})
        b = chunk({"Y": -4, "block_states": stone, "biomes": {"palette": ["minecraft:plains"]}})
        self.assertEqual(categories(a, b), [])

    def test_the_fallback_reports_what_no_function_names_and_ignores_list_order(self):
        (category, text), = compare_all._unexplained(chunk(section(-4), Odd=1), chunk(section(-4), Odd=2))
        self.assertEqual(category, "unexplained")
        self.assertIn("Odd", text)
        first, second = {"i": "minecraft:water", "x": 1}, {"i": "minecraft:lava", "x": 2}
        self.assertEqual(list(compare_all._unexplained(chunk(block_ticks=[first, second]),
                                                       chunk(block_ticks=[second, first]))), [])

    def test_count_mismatch_is_a_safety_net_when_nothing_else_is_found(self):
        from collections import Counter
        found = list(compare_all.coverage_mismatches("d", Counter(a=1), Counter(a=2), masking=False))
        self.assertEqual([d.category for d in found], ["unexplained"])
        self.assertEqual(list(compare_all.coverage_mismatches(
            "d", Counter({"scheduled ticks": 1}), Counter({"scheduled ticks": 2}), masking=True)), [])


def write_run(root: Path, name: str, *, family="mojang", sha="s1", regions=b"a", variant=None, location=None,
              bootstrap=None) -> Path:
    folder = root / name / "loc"
    (folder / "world" / "dimensions" / "minecraft" / "overworld" / "region").mkdir(parents=True)
    (folder / "world" / "dimensions" / "minecraft" / "overworld" / "region" / "r.0.0.mca").write_bytes(regions)
    bootstrap = bootstrap or (root / "boot" / "world")
    bootstrap.mkdir(parents=True, exist_ok=True)
    if not (bootstrap / "level.dat").exists():
        (bootstrap / "level.dat").write_bytes(b"boot")
    run = {"jar": "j.jar", "jar_sha256": "0" * 64, "family": family, "java": "25",
           "location": location or LOCATION._asdict(), "bootstrap_world": str(bootstrap),
           "bootstrap_sha256": sha_of(bootstrap) if sha == "s1" else sha,
           "variant": variant or {"order": "canonical", "without_structures": False, "overlay": None}}
    (folder / "run.json").write_text(json.dumps(run), encoding="utf-8")
    return root / name


def sha_of(world: Path) -> str:
    import worldcopy
    return worldcopy.digest(world)


class ComparableRunsTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def sides(self, a: Path, b: Path):
        return runinfo.read_run(a), runinfo.read_run(b)

    def test_two_different_runs_are_comparable(self):
        a, b = self.sides(write_run(self.root, "a", regions=b"a"), write_run(self.root, "b", regions=b"b"))
        self.assertEqual(runinfo.check_pair(a, b), [])

    def test_the_same_folder_is_refused(self):
        run = write_run(self.root, "a")
        with self.assertRaisesRegex(runinfo.Refused, "same folder"):
            runinfo.check_pair(*self.sides(run, run))

    def test_byte_identical_regions_are_refused_unless_asked_for(self):
        a, b = self.sides(write_run(self.root, "a"), write_run(self.root, "b"))
        with self.assertRaisesRegex(runinfo.Refused, "byte-identical"):
            runinfo.check_pair(a, b)
        self.assertEqual(runinfo.check_pair(a, b, allow_identical=True), [])

    def test_a_different_rectangle_is_refused(self):
        other = LOCATION._replace(x1=5)._asdict()
        a, b = self.sides(write_run(self.root, "a", regions=b"a"), write_run(self.root, "b", regions=b"b", location=other))
        with self.assertRaisesRegex(runinfo.Refused, "swept"):
            runinfo.check_pair(a, b)

    def test_a_different_variant_is_refused_unless_asked_for(self):
        variant = {"order": "whole-rectangle", "without_structures": False, "overlay": None}
        a, b = self.sides(write_run(self.root, "a", regions=b"a"), write_run(self.root, "b", regions=b"b", variant=variant))
        with self.assertRaisesRegex(runinfo.Refused, "variant"):
            runinfo.check_pair(a, b)
        self.assertEqual(runinfo.check_pair(a, b, allow_variant_mismatch=True), [])

    def test_different_bootstraps_are_refused(self):
        a = write_run(self.root, "a", regions=b"a")
        other = self.root / "boot2" / "world"
        other.mkdir(parents=True)
        (other / "level.dat").write_bytes(b"another")
        b = write_run(self.root, "b", regions=b"b", bootstrap=other)
        with self.assertRaisesRegex(runinfo.Refused, "different bootstraps"):
            runinfo.check_pair(*self.sides(a, b))

    def test_a_changed_bootstrap_is_refused(self):
        a, b = write_run(self.root, "a", regions=b"a"), write_run(self.root, "b", regions=b"b")
        (self.root / "boot" / "world" / "extra").write_bytes(b"x")
        with self.assertRaisesRegex(runinfo.Refused, "changed since"):
            runinfo.check_pair(*self.sides(a, b))

    def test_a_profile_that_disagrees_with_the_runs_is_refused(self):
        side = runinfo.read_run(write_run(self.root, "a"))
        runinfo.check_expected(side, [LOCATION])
        with self.assertRaisesRegex(runinfo.Refused, "leave --profile"):
            runinfo.check_expected(side, [LOCATION._replace(x1=0, z1=0)])

    def test_mojang_sides_come_from_the_recorded_family(self):
        a, b = self.sides(write_run(self.root, "a", family="mojang", regions=b"a"),
                          write_run(self.root, "b", family="paper", regions=b"b"))
        self.assertEqual(runinfo.mojang_sides(a, b), frozenset("A"))


class RunnerTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_jfr_is_looked_up_next_to_the_resolved_java(self):
        java = self.root / "jdk" / "bin" / "java"
        self.assertEqual(runs.jfr_tool(java), (self.root / "jdk" / "bin").resolve() / "jfr")

    def test_a_missing_java_is_an_error_not_a_path(self):
        with self.assertRaises(server.ServerError):
            runs.resolve_java(Path("no-such-java-for-the-test"))

    def test_the_default_java_follows_the_environment(self):
        saved = os.environ.pop("JAVA", None)
        try:
            os.environ["JAVA"] = "/opt/jdk-25/bin/java"
            self.assertEqual(parity.default_java(), Path("/opt/jdk-25/bin/java"))
            del os.environ["JAVA"]
            self.assertIn(parity.default_java(), (parity.ADOPTIUM_25, Path("java")))
            self.assertTrue(parity.default_java() == Path("java") or parity.ADOPTIUM_25.is_file())
        finally:
            os.environ.pop("JAVA", None)
            if saved is not None:
                os.environ["JAVA"] = saved

    def test_an_existing_bootstrap_folder_is_refused(self):
        existing = self.root / "boot"
        existing.mkdir()
        setup = runs.Setup(Path("java"), "1G", Path("m.jar"), False, Path("jfr"))
        with self.assertRaises(FileExistsError):
            runs.bootstrap_location(setup, LOCATION, existing)

    def test_an_overlay_with_server_properties_is_refused_before_anything_is_created(self):
        overlay = self.root / "overlay"
        overlay.mkdir()
        (overlay / "server.properties").write_text("server-ip=0.0.0.0\n", encoding="utf-8")
        setup = runs.Setup(Path("java"), "1G", Path("m.jar"), False, Path("jfr"))
        folder = self.root / "run"
        with self.assertRaisesRegex(server.ServerError, "bind address"):
            runs.measured_run(setup, Path("j.jar"), LOCATION, self.root, folder, runs.Variant(overlay=overlay))
        self.assertFalse(folder.exists())

    def test_the_properties_on_disk_are_checked_before_start(self):
        folder = self.root / "srv"
        folder.mkdir()
        text = "".join(f"{key}={value}\n" for key, value in server.BASE_PROPERTIES.items())
        (folder / "server.properties").write_text(text, encoding="utf-8")
        server.check_properties_on_disk(folder)
        (folder / "server.properties").write_text(text.replace("server-ip=127.0.0.1", "server-ip=0.0.0.0"),
                                                  encoding="utf-8")
        with self.assertRaisesRegex(server.ServerError, "refusing to start"):
            server.check_properties_on_disk(folder)
        (folder / "server.properties").write_text(text.replace("server-ip=127.0.0.1\n", ""), encoding="utf-8")
        with self.assertRaises(server.ServerError):
            server.check_properties_on_disk(folder)  # a missing ip means "all interfaces"

    def test_verdict_ranking_puts_partial_between_same_and_different(self):
        self.assertEqual(parity.worse(parity.EXIT_SAME, parity.EXIT_PARTIAL), parity.EXIT_PARTIAL)
        self.assertEqual(parity.worse(parity.EXIT_PARTIAL, parity.EXIT_DIFFERENT), parity.EXIT_DIFFERENT)
        self.assertEqual(parity.worse(parity.EXIT_DIFFERENT, parity.EXIT_NO_VERDICT), parity.EXIT_NO_VERDICT)


if __name__ == "__main__":
    unittest.main()
