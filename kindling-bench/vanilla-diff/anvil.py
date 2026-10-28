"""A reader for Anvil region files (r.<x>.<z>.mca) and the chunks inside them.

Handles the four chunk compressions of the format: gzip (1), zlib (2), none (3) and LZ4 (4, the block framing of
lz4-java that Minecraft uses), and chunks stored outside the region file (c.<x>.<z>.mcc).
"""
import gzip
import re
import struct
import zlib
from pathlib import Path
from typing import Iterator, NamedTuple

import nbt

SECTOR_BYTES = 4096
CHUNKS_PER_SIDE = 32
HEADER_BYTES = 2 * SECTOR_BYTES
EXTERNAL_FLAG = 0x80
COMPRESSION_GZIP, COMPRESSION_ZLIB, COMPRESSION_NONE, COMPRESSION_LZ4 = 1, 2, 3, 4

_REGION_NAME = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")
_LZ4_MAGIC = b"LZ4Block"
_LZ4_METHOD_RAW, _LZ4_METHOD_LZ4 = 0x10, 0x20
_LZ4_BLOCK_HEADER = struct.Struct("<BIII")  # token, compressed length, original length, checksum


class AnvilError(ValueError):
    pass


class ChunkRecord(NamedTuple):
    chunk_x: int
    chunk_z: int
    payload: bytes | None  # None when the chunk could not be read
    error: str | None


def region_files(directory: Path) -> Iterator[tuple[int, int, Path]]:
    """Every r.<x>.<z>.mca in the directory with its region coordinates, in a stable order."""
    for path in sorted(directory.glob("r.*.*.mca")):
        match = _REGION_NAME.match(path.name)
        if match:
            yield int(match.group(1)), int(match.group(2)), path


def read_chunks(region_path: Path, region_x: int, region_z: int) -> Iterator[ChunkRecord]:
    """Yields every stored chunk of one region file. A damaged chunk is yielded with its error instead of raising,
    so that one broken chunk shows up in the report and does not hide the rest of the world."""
    data = region_path.read_bytes()
    if len(data) < HEADER_BYTES:
        raise AnvilError(f"{region_path.name}: shorter than the 8 KiB header")
    for index in range(CHUNKS_PER_SIDE * CHUNKS_PER_SIDE):
        sector_offset, sector_count = _location(data, index)
        if sector_offset == 0 and sector_count == 0:
            continue
        chunk_x = region_x * CHUNKS_PER_SIDE + index % CHUNKS_PER_SIDE
        chunk_z = region_z * CHUNKS_PER_SIDE + index // CHUNKS_PER_SIDE
        try:
            payload = _chunk_payload(data, sector_offset, region_path.parent, chunk_x, chunk_z)
            yield ChunkRecord(chunk_x, chunk_z, payload, None)
        except (AnvilError, zlib.error, OSError, EOFError) as error:
            yield ChunkRecord(chunk_x, chunk_z, None, f"{type(error).__name__}: {error}")


def parse_chunk(payload: bytes) -> dict:
    return nbt.parse_root(payload)


def _location(data: bytes, index: int) -> tuple[int, int]:
    entry = data[index * 4:index * 4 + 4]
    return int.from_bytes(entry[:3], "big"), entry[3]


def _chunk_payload(data: bytes, sector_offset: int, directory: Path, chunk_x: int, chunk_z: int) -> bytes:
    start = sector_offset * SECTOR_BYTES
    if start + 5 > len(data):
        raise AnvilError("the chunk lies beyond the end of the region file")
    length, compression = struct.unpack_from(">iB", data, start)
    if compression & EXTERNAL_FLAG:
        stored = (directory / f"c.{chunk_x}.{chunk_z}.mcc").read_bytes()
    else:
        stored = data[start + 5:start + 4 + length]
        if len(stored) != length - 1:
            raise AnvilError("the chunk is cut off at the end of the region file")
    return decompress(stored, compression & ~EXTERNAL_FLAG)


def decompress(stored: bytes, compression: int) -> bytes:
    if compression == COMPRESSION_ZLIB:
        return zlib.decompress(stored)
    if compression == COMPRESSION_GZIP:
        return gzip.decompress(stored)
    if compression == COMPRESSION_NONE:
        return stored
    if compression == COMPRESSION_LZ4:
        return lz4_block_stream(stored)
    raise AnvilError(f"unsupported chunk compression {compression}")


def lz4_block_stream(stored: bytes) -> bytes:
    """Reads the framing of lz4-java's LZ4BlockOutputStream: the magic, then blocks of a header and a payload,
    ended by a block of length zero. The checksums are not verified."""
    if stored[:len(_LZ4_MAGIC)] != _LZ4_MAGIC:
        raise AnvilError("the LZ4 stream does not start with the LZ4Block magic")
    offset = len(_LZ4_MAGIC)
    out = bytearray()
    while offset < len(stored):
        token, compressed_length, original_length, _checksum = _LZ4_BLOCK_HEADER.unpack_from(stored, offset)
        offset += _LZ4_BLOCK_HEADER.size
        block = stored[offset:offset + compressed_length]
        offset += compressed_length
        if original_length == 0:
            break
        out += _lz4_block(block, original_length, token & 0xF0)
    return bytes(out)


def _lz4_block(block: bytes, original_length: int, method: int) -> bytes:
    if method == _LZ4_METHOD_RAW:
        return block
    if method != _LZ4_METHOD_LZ4:
        raise AnvilError(f"unknown LZ4 block method {method:#x}")
    out = bytearray()
    position = 0
    while position < len(block):
        token = block[position]
        position += 1
        literal_length, position = _extended_length(block, position, token >> 4)
        out += block[position:position + literal_length]
        position += literal_length
        if position >= len(block):
            break
        offset = block[position] | block[position + 1] << 8
        position += 2
        match_length, position = _extended_length(block, position, token & 0x0F)
        _copy_match(out, offset, match_length + 4)
    if len(out) != original_length:
        raise AnvilError(f"an LZ4 block expanded to {len(out)} bytes, expected {original_length}")
    return bytes(out)


def _extended_length(block: bytes, position: int, nibble: int) -> tuple[int, int]:
    length = nibble
    if nibble == 15:
        while True:
            extra = block[position]
            position += 1
            length += extra
            if extra != 255:
                break
    return length, position


def _copy_match(out: bytearray, offset: int, length: int) -> None:
    if offset == 0 or offset > len(out):
        raise AnvilError("an LZ4 match points before the start of the block")
    start = len(out) - offset
    for i in range(length):  # byte by byte: a match may overlap the bytes it is writing
        out.append(out[start + i])
