"""Tests of the comparer on synthetic region files.   python -I test_compare_worlds.py"""
import gzip
import json
import os
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # python -I does not add the script directory

import anvil  # noqa: E402
import chunkdiff  # noqa: E402
import nbt  # noqa: E402
import worlddiff  # noqa: E402

HERE = Path(__file__).resolve().parent
SECTOR = 4096
AIR = {"Name": "minecraft:air"}
STONE = {"Name": "minecraft:stone"}
PISTON = {"Name": "minecraft:piston", "Properties": {"facing": "east", "extended": "false"}}


# ---- test-side writers: NBT, packed palettes, region files -------------------------------------------------------

class Long(int):
    pass


class LongArray(tuple):
    pass


def _name(text: str) -> bytes:
    raw = text.encode()
    return struct.pack(">H", len(raw)) + raw


def _tag_id(value) -> int:
    if isinstance(value, dict):
        return 10
    if isinstance(value, list):
        return 9
    if isinstance(value, LongArray):
        return 12
    if isinstance(value, Long):
        return 4
    if isinstance(value, str):
        return 8
    if isinstance(value, float):
        return 6
    return 3


def _payload(value) -> bytes:
    kind = _tag_id(value)
    if kind == 10:
        return b"".join(bytes([_tag_id(v)]) + _name(k) + _payload(v) for k, v in value.items()) + b"\x00"
    if kind == 9:
        inner = _tag_id(value[0]) if value else 0
        return bytes([inner]) + struct.pack(">i", len(value)) + b"".join(_payload(v) for v in value)
    if kind == 12:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">q", v) for v in value)
    if kind == 4:
        return struct.pack(">q", value)
    if kind == 8:
        return _name(value)
    if kind == 6:
        return struct.pack(">d", value)
    return struct.pack(">i", value)


def encode_nbt(root: dict) -> bytes:
    return b"\x0a" + _name("") + _payload(root)


def pack_indices(indices: list[int], bits: int) -> LongArray:
    per_long = 64 // bits
    longs = []
    for start in range(0, len(indices), per_long):
        value = 0
        for slot, index in enumerate(indices[start:start + per_long]):
            value |= index << (slot * bits)
        longs.append(value - (1 << 64) if value >= 1 << 63 else value)
    return LongArray(longs)


def container(palette: list, indices: list[int] | None, min_bits: int) -> dict:
    result = {"palette": palette}
    if len(palette) > 1:
        bits = max(min_bits, (len(palette) - 1).bit_length())
        result["data"] = pack_indices(indices, bits)
    return result


def block_section(y: int, palette: list, indices: list[int] | None = None, biomes: list[str] | None = None) -> dict:
    biome_palette = biomes or ["minecraft:plains"]
    biome_indices = list(range(len(biome_palette))) * 64
    return {
        "Y": y,
        "block_states": container(palette, indices, 4),
        "biomes": container(biome_palette, biome_indices[:64], 1),
    }


def with_block(section_y: int, index: int, state: dict) -> dict:
    indices = [0] * 4096
    indices[index] = 1
    return block_section(section_y, [AIR, state], indices)


def chunk_nbt(chunk_x: int, chunk_z: int, sections: list, **extra) -> dict:
    return {"xPos": chunk_x, "zPos": chunk_z, "Status": "minecraft:full", "LastUpdate": Long(0),
            "sections": sections, **extra}


def lz4_stream(blocks: list[tuple[int, bytes, int]]) -> bytes:
    """blocks: (method token, payload, original length)"""
    out = b"LZ4Block"
    for token, payload, original in blocks + [(0x10, b"", 0)]:
        out += struct.pack("<BIII", token, len(payload), original, 0) + payload
    return out


def write_region(path: Path, chunks: dict[tuple[int, int], tuple[int, bytes]]) -> None:
    """chunks: (local x, local z) -> (compression byte, stored bytes). Writes one chunk per sector run."""
    header, body = bytearray(2 * SECTOR), bytearray()
    for (local_x, local_z), (compression, stored) in chunks.items():
        record = struct.pack(">iB", len(stored) + 1, compression) + stored
        record += b"\x00" * (-len(record) % SECTOR)
        slot = (local_z * 32 + local_x) * 4
        header[slot:slot + 4] = ((2 + len(body) // SECTOR) << 8 | len(record) // SECTOR).to_bytes(4, "big")
        body += record
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(header) + bytes(body))


def write_chunks(level: Path, chunks: dict[tuple[int, int], dict], folder: str = "region") -> None:
    """Writes chunk compounds, keyed by chunk coordinates, into zlib region files of region 0,0."""
    stored = {key: (anvil.COMPRESSION_ZLIB, zlib.compress(encode_nbt(chunk))) for key, chunk in chunks.items()}
    write_region(level / folder / "r.0.0.mca", stored)


def make_level(root: Path, name: str, chunks: dict, entities: dict | None = None) -> Path:
    level = root / name
    write_chunks(level, chunks)
    if entities:
        write_chunks(level, {key: {"Position": [key[0], key[1]], "Entities": mobs} for key, mobs in entities.items()},
                     "entities")
    return level


class TempDirTestCase(unittest.TestCase):
    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.root = Path(self._temp.name)

    def compare(self, chunks_a, chunks_b, entities_a=None, entities_b=None, areas=None):
        level_a = make_level(self.root, "a", chunks_a, entities_a)
        level_b = make_level(self.root, "b", chunks_b, entities_b)
        return worlddiff.compare_levels(level_a, level_b, areas)


# ---- nbt and anvil -----------------------------------------------------------------------------------------------

class NbtTest(unittest.TestCase):
    def test_reads_nested_values_and_arrays(self):
        root = {"name": "x", "n": 7, "d": 1.5, "list": [1, 2], "arr": LongArray((1, -2)), "inner": {"k": "v"}}
        parsed = nbt.parse_root(encode_nbt(root))
        self.assertEqual({**root, "arr": (1, -2)}, parsed)

    def test_truncated_data_raises(self):
        with self.assertRaises(nbt.NbtError):
            nbt.parse_root(encode_nbt({"name": "x"})[:-3])


class AnvilTest(TempDirTestCase):
    def read_single_chunk(self, compression: int, stored: bytes) -> bytes:
        path = self.root / "r.0.0.mca"
        write_region(path, {(3, 2): (compression, stored)})
        records = list(anvil.read_chunks(path, 0, 0))
        self.assertEqual([(3, 2)], [(r.chunk_x, r.chunk_z) for r in records])
        self.assertIsNone(records[0].error)
        return records[0].payload

    def test_zlib_gzip_and_uncompressed_chunks(self):
        data = encode_nbt({"v": 1})
        self.assertEqual(data, self.read_single_chunk(anvil.COMPRESSION_ZLIB, zlib.compress(data)))
        self.assertEqual(data, self.read_single_chunk(anvil.COMPRESSION_GZIP, gzip.compress(data)))
        self.assertEqual(data, self.read_single_chunk(anvil.COMPRESSION_NONE, data))

    def test_lz4_stream_with_raw_and_compressed_blocks(self):
        data = encode_nbt({"v": 1})
        compressed = bytes([0x32]) + b"abc" + b"\x03\x00" + bytes([0x10]) + b"Z"
        raw_stream = lz4_stream([(0x10, data, len(data))])
        self.assertEqual(data, self.read_single_chunk(anvil.COMPRESSION_LZ4, raw_stream))
        self.assertEqual(b"abcabcabcZ", anvil.lz4_block_stream(lz4_stream([(0x29, compressed, 10)])))

    def test_lz4_without_magic_is_an_error(self):
        with self.assertRaises(anvil.AnvilError):
            anvil.lz4_block_stream(b"nope")

    def test_external_chunk_is_read_from_its_mcc_file(self):
        data = encode_nbt({"v": 1})
        (self.root / "c.3.2.mcc").write_bytes(zlib.compress(data))
        path = self.root / "r.0.0.mca"
        write_region(path, {(3, 2): (anvil.COMPRESSION_ZLIB | anvil.EXTERNAL_FLAG, b"")})
        (record,) = anvil.read_chunks(path, 0, 0)
        self.assertEqual(data, record.payload)

    def test_damaged_chunk_is_reported_and_does_not_hide_the_others(self):
        path = self.root / "r.0.0.mca"
        write_region(path, {(0, 0): (anvil.COMPRESSION_ZLIB, b"not zlib"),
                            (1, 0): (anvil.COMPRESSION_NONE, encode_nbt({"v": 1}))})
        records = {(r.chunk_x, r.chunk_z): r for r in anvil.read_chunks(path, 0, 0)}
        self.assertIsNotNone(records[(0, 0)].error)
        self.assertIsNone(records[(1, 0)].error)

    def test_region_coordinates_offset_the_chunk_coordinates(self):
        path = self.root / "r.-1.2.mca"
        write_region(path, {(31, 0): (anvil.COMPRESSION_NONE, encode_nbt({"v": 1}))})
        (record,) = anvil.read_chunks(path, -1, 2)
        self.assertEqual((-1, 64), (record.chunk_x, record.chunk_z))


class PackedPaletteTest(unittest.TestCase):
    def test_unpack_roundtrips_with_padding_in_the_last_long(self):
        indices = [i % 5 for i in range(4096)]
        self.assertEqual(indices, chunkdiff.unpack_indices(pack_indices(indices, 4), 4, 4096))

    def test_unpack_five_bit_entries_never_span_longs(self):
        indices = [i % 20 for i in range(4096)]
        packed = pack_indices(indices, 5)
        self.assertEqual(4096 // 12 + 1, len(packed))
        self.assertEqual(indices, chunkdiff.unpack_indices(packed, 5, 4096))

    def test_too_little_data_is_an_error(self):
        with self.assertRaises(chunkdiff.ChunkFormatError):
            chunkdiff.unpack_indices(LongArray((0,)), 4, 4096)


# ---- the comparer ------------------------------------------------------------------------------------------------

class BlockDiffTest(TempDirTestCase):
    def test_identical_worlds_have_no_differences(self):
        chunks = {(0, 0): chunk_nbt(0, 0, [with_block(0, 5, STONE)])}
        report = self.compare(chunks, chunks)
        self.assertEqual([], report.differences)
        self.assertEqual(1, report.chunks_compared)

    def test_a_different_block_is_reported_at_its_world_position(self):
        index = 3 * 256 + 5 * 16 + 7  # local y 3, z 5, x 7
        a = {(2, 1): chunk_nbt(2, 1, [with_block(-4, index, STONE)])}
        b = {(2, 1): chunk_nbt(2, 1, [with_block(-4, index, PISTON)])}
        (difference,) = self.compare(a, b).differences
        self.assertEqual("block", difference.category)
        self.assertEqual("(39, -61, 21) minecraft:stone -> minecraft:piston[extended=false,facing=east]",
                         difference.description)

    def test_a_missing_section_equals_an_all_air_section(self):
        a = {(0, 0): chunk_nbt(0, 0, [])}
        b = {(0, 0): chunk_nbt(0, 0, [block_section(0, [AIR])])}
        self.assertEqual([], self.compare(a, b).differences)

    def test_the_same_blocks_in_a_different_palette_order_are_equal(self):
        indices_a = [0] * 4096
        indices_a[10] = 1
        indices_b = [1] * 4096
        indices_b[10] = 0
        a = {(0, 0): chunk_nbt(0, 0, [block_section(0, [AIR, STONE], indices_a)])}
        b = {(0, 0): chunk_nbt(0, 0, [block_section(0, [STONE, AIR], indices_b)])}
        self.assertEqual([], self.compare(a, b).differences)

    def test_a_different_biome_is_reported_per_quart_cell(self):
        a = {(1, 0): chunk_nbt(1, 0, [block_section(0, [AIR], biomes=["minecraft:plains"])])}
        b = {(1, 0): chunk_nbt(1, 0, [block_section(0, [AIR], biomes=["minecraft:desert"])])}
        differences = self.compare(a, b).differences
        self.assertEqual(64, len(differences))
        self.assertEqual("quart (4, 0, 0) minecraft:plains -> minecraft:desert", differences[0].description)

    def test_ignored_chunk_fields_do_not_count(self):
        a = {(0, 0): chunk_nbt(0, 0, [], InhabitedTime=Long(5), isLightOn=1)}
        b = {(0, 0): {**chunk_nbt(0, 0, [], InhabitedTime=Long(900)), "DataVersion": 1}}
        self.assertEqual([], self.compare(a, b).differences)

    def test_scheduled_ticks_are_compared(self):
        tick = {"i": "minecraft:piston", "t": 2, "p": 0, "x": 1, "y": 2, "z": 3}
        a = {(0, 0): chunk_nbt(0, 0, [], block_ticks=[tick])}
        b = {(0, 0): chunk_nbt(0, 0, [], block_ticks=[{**tick, "t": 3}])}
        self.assertEqual({"scheduled tick"}, {d.category for d in self.compare(a, b).differences})


class ChunkSelectionTest(TempDirTestCase):
    def test_chunks_that_are_not_full_are_skipped_when_both_are_not_full(self):
        partial = {**chunk_nbt(0, 0, [with_block(0, 1, STONE)]), "Status": "minecraft:features"}
        other = {**chunk_nbt(0, 0, [with_block(0, 1, PISTON)]), "Status": "minecraft:carvers"}
        report = self.compare({(0, 0): partial}, {(0, 0): other})
        self.assertEqual(([], 1), (report.differences, report.chunks_skipped))

    def test_full_in_one_world_and_not_in_the_other_is_a_difference(self):
        full = chunk_nbt(0, 0, [])
        partial = {**full, "Status": "minecraft:features"}
        (difference,) = self.compare({(0, 0): full}, {(0, 0): partial}).differences
        self.assertIn("status differs", difference.description)

    def test_a_full_chunk_missing_in_the_other_world_is_a_difference(self):
        both = {(0, 0): chunk_nbt(0, 0, []), (1, 0): chunk_nbt(1, 0, [])}
        (difference,) = self.compare(both, {(0, 0): chunk_nbt(0, 0, [])}).differences
        self.assertEqual((1, 0), difference.chunk)
        self.assertIn("only in A", difference.description)
        self.assertEqual("chunk", difference.category)

    def test_areas_restrict_what_is_compared(self):
        a = {(0, 0): chunk_nbt(0, 0, [with_block(0, 1, STONE)]), (5, 0): chunk_nbt(5, 0, [with_block(0, 1, STONE)])}
        b = {(0, 0): chunk_nbt(0, 0, [with_block(0, 1, STONE)]), (5, 0): chunk_nbt(5, 0, [with_block(0, 1, PISTON)])}
        self.assertEqual([], self.compare(a, b, areas={worlddiff.OVERWORLD: [(0, 0, 3, 3)]}).differences)
        inside = self.compare(a, b, areas={worlddiff.OVERWORLD: [(4, 0, 6, 0)]})
        self.assertEqual({(5, 0)}, {d.chunk for d in inside.differences})

    def test_an_unreadable_chunk_is_a_difference(self):
        level_a = make_level(self.root, "a", {(0, 0): chunk_nbt(0, 0, [])})
        level_b = self.root / "b"
        write_region(level_b / "region" / "r.0.0.mca", {(0, 0): (anvil.COMPRESSION_ZLIB, b"broken")})
        (difference,) = worlddiff.compare_levels(level_a, level_b).differences
        self.assertIn("unreadable in B", difference.description)


class BlockEntityDiffTest(TempDirTestCase):
    def chunk_with(self, **block_entity):
        entity = {"id": "minecraft:chest", "x": 1, "y": 2, "z": 3, **block_entity}
        return {(0, 0): chunk_nbt(0, 0, [], block_entities=[entity])}

    def test_volatile_keys_are_ignored(self):
        a = self.chunk_with(**{"Paper.Origin": "x", "keepPacked": 1, "Items": []})
        b = self.chunk_with(**{"Spigot.ticksLived": 40, "Items": []})
        self.assertEqual([], self.compare(a, b).differences)

    def test_item_count_changes_report_container_totals(self):
        def stack(count):
            return [{"Slot": 0, "id": "minecraft:stone", "count": count}]
        differences = self.compare(self.chunk_with(Items=stack(3)), self.chunk_with(Items=stack(2))).differences
        self.assertEqual({"block entity", "container"}, {d.category for d in differences})
        container_line = next(d for d in differences if d.category == "container")
        self.assertIn("totals differ", container_line.description)
        self.assertIn("'minecraft:stone': 3", container_line.description)

    def test_moved_items_with_equal_totals_say_so(self):
        def stack(slot):
            return [{"Slot": slot, "id": "minecraft:stone", "count": 3}]
        differences = self.compare(self.chunk_with(Items=stack(0)), self.chunk_with(Items=stack(1))).differences
        self.assertIn("totals equal, slots differ", next(d for d in differences if d.category == "container").description)

    def test_a_block_entity_only_in_one_world_is_reported(self):
        (difference,) = self.compare(self.chunk_with(), {(0, 0): chunk_nbt(0, 0, [])}).differences
        self.assertEqual("(1, 2, 3) minecraft:chest only in A", difference.description)


class EntityDiffTest(TempDirTestCase):
    ITEM = {"id": "minecraft:item", "Pos": [1.5, 2.0, 3.5], "Motion": [0.0, 0.0, 0.0], "Age": 10,
            "Item": {"id": "minecraft:stone", "count": 1}}

    def compare_entities(self, mobs_a, mobs_b):
        empty = {(0, 0): chunk_nbt(0, 0, [])}
        return self.compare(empty, empty, {(0, 0): mobs_a}, {(0, 0): mobs_b}).differences

    def test_uuids_and_server_tags_are_ignored(self):
        a = {**self.ITEM, "UUID": [1, 2, 3, 4], "Paper.Origin": [0.0, 0.0, 0.0]}
        b = {**self.ITEM, "UUID": [9, 9, 9, 9], "Spigot.ticksLived": 40}
        self.assertEqual([], self.compare_entities([a], [b]))

    def test_order_inside_a_chunk_does_not_matter(self):
        other = {**self.ITEM, "Pos": [4.5, 2.0, 3.5]}
        self.assertEqual([], self.compare_entities([self.ITEM, other], [other, self.ITEM]))

    def test_a_moved_item_is_one_missing_and_one_extra_entity(self):
        moved = {**self.ITEM, "Pos": [1.25, 2.0, 3.5]}
        differences = self.compare_entities([self.ITEM], [moved])
        self.assertEqual(["only in A", "only in B"], [d.description.split(":")[0] for d in differences])

    def test_two_items_against_one_is_reported(self):
        self.assertEqual(1, len(self.compare_entities([self.ITEM, self.ITEM], [self.ITEM])))


class DimensionDiscoveryTest(TempDirTestCase):
    def test_finds_the_overworld_legacy_dimension_folders_and_the_new_layout(self):
        level = self.root / "world"
        for relative in ("region", "DIM-1/region", "DIM1/region", "dimensions/mymod/caves/region"):
            (level / relative).mkdir(parents=True)
        (self.root / "world_nether" / "DIM-1" / "region").mkdir(parents=True)
        found = worlddiff.find_dimensions(level)
        self.assertEqual({worlddiff.OVERWORLD, worlddiff.NETHER, worlddiff.END, "mymod:caves"}, set(found))

    def test_a_dimension_missing_in_one_level_is_a_difference(self):
        (self.root / "a" / "region").mkdir(parents=True)
        (self.root / "a" / "DIM1" / "region").mkdir(parents=True)
        (self.root / "b" / "region").mkdir(parents=True)
        (difference,) = worlddiff.compare_levels(self.root / "a", self.root / "b").differences
        self.assertEqual(("minecraft:the_end", "missing in B"), (difference.dimension, difference.description))


class CommandLineTest(TempDirTestCase):
    def run_cli(self, *args) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, "-I", str(HERE / "compare_worlds.py"), *map(str, args)],
                              capture_output=True, text=True)

    def test_exit_code_and_report_for_identical_and_different_worlds(self):
        same = {(0, 0): chunk_nbt(0, 0, [with_block(0, 1, STONE)])}
        different = {(0, 0): chunk_nbt(0, 0, [with_block(0, 1, PISTON)])}
        level_a = make_level(self.root, "a", same)
        level_b = make_level(self.root, "b", same)
        level_c = make_level(self.root, "c", different)
        identical = self.run_cli(level_a, level_b)
        self.assertEqual((0, True), (identical.returncode, "RESULT: identical" in identical.stdout))
        changed = self.run_cli(level_a, level_c)
        self.assertEqual(1, changed.returncode)
        self.assertIn("block: (1, 0, 0) minecraft:stone -> minecraft:piston", changed.stdout)

    def test_areas_file_is_read(self):
        level = make_level(self.root, "a", {(0, 0): chunk_nbt(0, 0, [])})
        areas = self.root / "areas.json"
        areas.write_text(json.dumps({worlddiff.OVERWORLD: [[0, 0, 0, 0]]}))
        self.assertEqual(0, self.run_cli(level, level, "--areas", areas).returncode)

    def test_list_ignored_names_the_paper_and_leaf_tags(self):
        result = self.run_cli("--list-ignored")
        self.assertEqual(0, result.returncode)
        self.assertIn("Paper.", result.stdout)
        self.assertIn("Leaf.", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
