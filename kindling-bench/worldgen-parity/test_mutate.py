"""Tests of the NBT and region writer, the planted changes and the comparison additions.   python -I test_mutate.py"""
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import paths  # noqa: E402,F401
import anvil  # noqa: E402
import chunkdiff  # noqa: E402
import compare_all  # noqa: E402
import mutate_region  # noqa: E402
import nbt  # noqa: E402
import nbtwrite  # noqa: E402
import sweep  # noqa: E402
from nbtwrite import Tag, compound  # noqa: E402

LOCATION = sweep.Location("t", "minecraft:overworld", 0, 0, 3, 0)


def strings(*values: str) -> Tag:
    return Tag(nbt.TAG_LIST, (nbt.TAG_STRING, [Tag(nbt.TAG_STRING, value) for value in values]))


def empty_list() -> Tag:
    return Tag(nbt.TAG_LIST, (nbt.TAG_END, []))


def synthetic_chunk(x: int, z: int) -> Tag:
    indices = [index % 2 for index in range(4096)]
    section = compound(
        Y=Tag(nbt.TAG_BYTE, -4),
        block_states=compound(palette=strings("minecraft:bedrock", "minecraft:stone"),
                              data=Tag(nbt.TAG_LONG_ARRAY, mutate_region.pack_indices(indices, 4))),
        biomes=compound(palette=strings("minecraft:plains")))
    return compound(
        DataVersion=Tag(nbt.TAG_INT, 4000), xPos=Tag(nbt.TAG_INT, x), zPos=Tag(nbt.TAG_INT, z),
        Status=Tag(nbt.TAG_STRING, "minecraft:full"),
        sections=Tag(nbt.TAG_LIST, (nbt.TAG_COMPOUND, [section])),
        structures=compound(starts=compound(), References=compound()),
        fluid_ticks=empty_list(), block_ticks=empty_list())


def synthetic_entities(x: int, z: int) -> Tag:
    cow = compound(id=Tag(nbt.TAG_STRING, "minecraft:cow"), Health=Tag(nbt.TAG_FLOAT, 10.0),
                   Pos=Tag(nbt.TAG_LIST, (nbt.TAG_DOUBLE, [Tag(nbt.TAG_DOUBLE, v) for v in (x * 16 + 0.5, 64.0, 0.5)])))
    return compound(DataVersion=Tag(nbt.TAG_INT, 4000), Entities=Tag(nbt.TAG_LIST, (nbt.TAG_COMPOUND, [cow])))


def write_world(root: Path) -> Path:
    world = root / "world"
    for folder, make in (("region", synthetic_chunk), ("entities", synthetic_entities)):
        directory = world / "dimensions" / "minecraft" / "overworld" / folder
        directory.mkdir(parents=True)
        (directory / "r.0.0.mca").write_bytes(bytes(anvil.HEADER_BYTES))
        for x, z in LOCATION.steps():
            nbtwrite.write_chunk(directory, x, z, "", make(x, z))
    return world


class TempWorldTestCase(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)


class NbtWriterTest(unittest.TestCase):
    def test_round_trip_is_byte_exact(self):
        data = nbtwrite.serialize("", synthetic_chunk(1, 2))
        name, root = nbtwrite.parse(data)
        self.assertEqual(nbtwrite.serialize(name, root), data)

    def test_the_value_reader_agrees(self):
        decoded = nbt.parse_root(nbtwrite.serialize("", synthetic_chunk(1, 2)))
        self.assertEqual(decoded["Status"], "minecraft:full")
        self.assertEqual(decoded["sections"][0]["block_states"]["palette"], ["minecraft:bedrock", "minecraft:stone"])

    def test_packing_is_the_inverse_of_unpacking(self):
        for bits in (1, 4, 5, 9):
            indices = [(i * 7) % (1 << bits) for i in range(4096)]
            self.assertEqual(chunkdiff.unpack_indices(mutate_region.pack_indices(indices, bits), bits, 4096), indices)


class RegionWriterTest(TempWorldTestCase):
    def test_a_rewritten_chunk_reads_back_and_the_others_stay(self):
        world = write_world(self.root)
        region = world / "dimensions" / "minecraft" / "overworld" / "region"
        name, root = nbtwrite.read_chunk(region, 1, 0)
        root.value["Status"] = Tag(nbt.TAG_STRING, "minecraft:features")
        nbtwrite.write_chunk(region, 1, 0, name, root)
        chunks = compare_all.load_chunks(region, (0, 0, 3, 0))
        self.assertEqual(chunks[(1, 0)]["Status"], "minecraft:features")
        self.assertEqual({key: chunk["Status"] for key, chunk in chunks.items() if key != (1, 0)},
                         {(0, 0): "minecraft:full", (2, 0): "minecraft:full", (3, 0): "minecraft:full"})


class PlantedChangesTest(TempWorldTestCase):
    def test_exactly_the_five_planted_changes_are_reported(self):
        original = write_world(self.root / "a")
        planted_world = self.root / "b" / "world"
        shutil.copytree(original, planted_world)
        planted = mutate_region.plant_all(planted_world, LOCATION)
        report = compare_all.compare_location(original, planted_world, LOCATION)
        self.assertEqual(sorted((d.category, d.chunk) for d in report.differences),
                         sorted((p.category, p.chunk) for p in planted))
        self.assertEqual(len(planted), 5)

    def test_identical_worlds_have_no_differences(self):
        world = write_world(self.root)
        self.assertEqual(compare_all.compare_location(world, world, LOCATION).differences, [])


def chunk(**fields) -> dict:
    return {"Status": "minecraft:full", "sections": [], **fields}


class ComparisonAdditionsTest(unittest.TestCase):
    def diff(self, a: dict, b: dict) -> list[tuple[str, str]]:
        return list(compare_all.diff_chunk((0, 0), a, b))

    def test_status_is_compared(self):
        self.assertEqual(self.diff(chunk(), chunk(Status="minecraft:features"))[0][0], "status")

    def test_structure_references_are_decoded(self):
        packed = (5 & 0xFFFFFFFF) | ((-3 & 0xFFFFFFFF) << 32)
        packed -= 1 << 64 if packed >= 1 << 63 else 0
        a = chunk(structures={"starts": {}, "References": {}})
        b = chunk(structures={"starts": {}, "References": {"minecraft:village": (packed,)}})
        (category, text), = self.diff(a, b)
        self.assertEqual(category, "structure reference")
        self.assertIn("(5, -3) only in B", text)

    def test_heightmaps_count_columns(self):
        values = [64] * 256
        changed = list(values)
        changed[17] = 70
        a = chunk(Heightmaps={"WORLD_SURFACE": mutate_region.pack_indices(values, 9)})
        b = chunk(Heightmaps={"WORLD_SURFACE": mutate_region.pack_indices(changed, 9)})
        self.assertEqual(self.diff(a, b), [("heightmap", "WORLD_SURFACE: 1 of 256 columns differ")])

    def test_post_processing_marks(self):
        self.assertEqual([c for c, _ in self.diff(chunk(PostProcessing=[[], [5]]), chunk(PostProcessing=[[], []]))],
                         ["post-processing"])

    def test_unknown_fields_are_not_silently_ignored(self):
        self.assertEqual(self.diff(chunk(), chunk(SomeForkField=1))[0][0], "chunk field")

    def test_ignored_fields_stay_ignored(self):
        self.assertEqual(self.diff(chunk(LastUpdate=1, isLightOn=1), chunk(LastUpdate=2)), [])

    def test_empty_container_items_mean_no_items(self):
        a = chunk(block_entities=[{"id": "minecraft:chest", "x": 0, "y": 0, "z": 0}])
        b = chunk(block_entities=[{"id": "minecraft:chest", "x": 0, "y": 0, "z": 0, "Items": []}])
        self.assertEqual(self.diff(a, b), [])

    def test_attribute_order_is_not_a_difference(self):
        first = {"id": "minecraft:cow", "Pos": [0.5, 64.0, 0.5],
                 "attributes": [{"id": "minecraft:scale", "base": 1.0}, {"id": "minecraft:armor", "base": 0.0}]}
        second = {**first, "attributes": list(reversed(first["attributes"])), "UUID": (1, 2, 3, 4)}
        self.assertEqual(list(compare_all.diff_entity_lists([first], [second])), [])

    def test_an_attribute_only_one_side_writes_is_named(self):
        cow = {"id": "minecraft:cow", "Pos": [0.5, 64.0, 0.5], "attributes": []}
        richer = {**cow, "attributes": [{"id": "minecraft:scale", "base": 1.0}]}
        (category, text), = compare_all.diff_entity_lists([cow], [richer])
        self.assertIn("attribute minecraft:scale only in B (base 1.0, no modifiers)", text)

    def test_promotion_race_signature(self):
        sections = [{"Y": -4}, {"Y": -3}]
        bootstrap = {(1, 1): chunk(PostProcessing=[[1, 2], []], sections=sections)}
        pending = chunk(PostProcessing=[[1, 2], []], sections=sections)
        done = chunk(PostProcessing=[[], []], sections=sections)
        mojang_a = frozenset("A")
        self.assertIn("in A", compare_all.promotion_races({(1, 1): pending}, {(1, 1): done}, bootstrap, mojang_a)[(1, 1)].reason)
        self.assertEqual(compare_all.promotion_races({(1, 1): done}, {(1, 1): done}, bootstrap, mojang_a), {})
        other_marks = chunk(PostProcessing=[[9], []], sections=sections)
        self.assertEqual(compare_all.promotion_races({(1, 1): other_marks}, {(1, 1): done}, bootstrap, mojang_a), {})

    def test_the_jar_under_test_is_never_excused(self):
        sections = [{"Y": -4}]
        bootstrap = {(1, 1): chunk(PostProcessing=[[1]], sections=sections)}
        pending, done = chunk(PostProcessing=[[1]], sections=sections), chunk(PostProcessing=[[]], sections=sections)
        # A is the jar under test and never post-processed: a difference, not a race
        self.assertEqual(compare_all.promotion_races({(1, 1): pending}, {(1, 1): done}, bootstrap, frozenset("B")), {})
        # two Mojang runs: a self-check shows it
        self.assertEqual(compare_all.promotion_races({(1, 1): pending}, {(1, 1): done}, bootstrap, frozenset("AB")), {})
        # nobody is known to be Mojang
        self.assertEqual(compare_all.promotion_races({(1, 1): pending}, {(1, 1): done}, bootstrap), {})

    def test_a_race_needs_a_footprint(self):
        bootstrap = {(1, 1): chunk(PostProcessing=[[1]], sections=[])}
        pending, done = chunk(PostProcessing=[[1]]), chunk(PostProcessing=[[]])
        self.assertEqual(compare_all.promotion_races({(1, 1): pending}, {(1, 1): done}, bootstrap, frozenset("A")), {})

    def test_post_processing_marks_unpack_as_x_y_z(self):
        packed = 3 | 5 << 4 | 7 << 8  # ProtoChunk.packOffsetCoordinates
        self.assertEqual(compare_all.unpack_post_processing((2, -1), -3, packed), (35, -43, -9))
        footprint = compare_all.post_processing_footprint((2, -1), chunk(sections=[{"Y": -4}, {"Y": -3}],
                                                                         PostProcessing=[[], [packed]]))
        self.assertEqual(len(footprint.positions), 7)
        self.assertIn((35, -43, -9), footprint.positions)
        self.assertIn((35, -42, -9), footprint.positions)

    def test_chunk_pos_unpacking(self):
        packed = (-7 & 0xFFFFFFFF) | ((123 & 0xFFFFFFFF) << 32)
        self.assertEqual(compare_all.unpack_chunk_pos(packed), (-7, 123))


if __name__ == "__main__":
    unittest.main()
