"""Typed NBT that can be written back byte for byte, and a region writer, for planting changes in saved worlds.

../vanilla-diff/nbt.py forgets tag types on purpose (the comparer only asks for values); rewriting a chunk needs
them. A Tag is (kind, value): a compound's value is a dict of name -> Tag, a list's value is (element kind, [Tag]),
arrays are tuples of int, everything else the plain Python value.
"""
import struct
import time
import zlib
from pathlib import Path
from typing import NamedTuple

import paths  # noqa: F401
import anvil
import nbt

_NUMBERS = {nbt.TAG_BYTE: ">b", nbt.TAG_SHORT: ">h", nbt.TAG_INT: ">i", nbt.TAG_LONG: ">q",
            nbt.TAG_FLOAT: ">f", nbt.TAG_DOUBLE: ">d"}
_ARRAYS = {nbt.TAG_BYTE_ARRAY: "b", nbt.TAG_INT_ARRAY: "i", nbt.TAG_LONG_ARRAY: "q"}


class Tag(NamedTuple):
    kind: int
    value: object


def compound(**children: Tag) -> Tag:
    return Tag(nbt.TAG_COMPOUND, dict(children))


def parse(data: bytes) -> tuple[str, Tag]:
    reader = _Reader(data)
    kind = reader.unpack(">b")
    name = reader.string()
    return name, Tag(kind, reader.payload(kind))


def serialize(name: str, root: Tag) -> bytes:
    out = bytearray(struct.pack(">b", root.kind))
    _string(out, name)
    _payload(out, root)
    return bytes(out)


class _Reader:
    def __init__(self, data: bytes):
        self.data, self.offset = data, 0

    def unpack(self, fmt: str):
        (value,) = struct.unpack_from(fmt, self.data, self.offset)
        self.offset += struct.calcsize(fmt)
        return value

    def string(self) -> str:
        length = self.unpack(">H")
        text = self.data[self.offset:self.offset + length].decode("utf-8", errors="surrogateescape")
        self.offset += length
        return text

    def payload(self, kind: int):
        if kind in _NUMBERS:
            return self.unpack(_NUMBERS[kind])
        if kind == nbt.TAG_STRING:
            return self.string()
        if kind in _ARRAYS:
            length = self.unpack(">i")
            values = struct.unpack_from(f">{length}{_ARRAYS[kind]}", self.data, self.offset)
            self.offset += length * struct.calcsize(_ARRAYS[kind])
            return values
        if kind == nbt.TAG_LIST:
            element_kind, length = self.unpack(">b"), self.unpack(">i")
            return element_kind, [Tag(element_kind, self.payload(element_kind)) for _ in range(length)]
        if kind == nbt.TAG_COMPOUND:
            children = {}
            while (child_kind := self.unpack(">b")) != nbt.TAG_END:
                name = self.string()
                children[name] = Tag(child_kind, self.payload(child_kind))
            return children
        raise nbt.NbtError(f"unknown tag type {kind} at byte {self.offset}")


def _string(out: bytearray, text: str) -> None:
    raw = text.encode("utf-8", errors="surrogateescape")
    out += struct.pack(">H", len(raw)) + raw


def _payload(out: bytearray, tag: Tag) -> None:
    kind, value = tag
    if kind in _NUMBERS:
        out += struct.pack(_NUMBERS[kind], value)
    elif kind == nbt.TAG_STRING:
        _string(out, value)
    elif kind in _ARRAYS:
        out += struct.pack(f">i{len(value)}{_ARRAYS[kind]}", len(value), *value)
    elif kind == nbt.TAG_LIST:
        element_kind, elements = value
        out += struct.pack(">bi", element_kind, len(elements))
        for element in elements:
            _payload(out, element)
    elif kind == nbt.TAG_COMPOUND:
        for name, child in value.items():
            out += struct.pack(">b", child.kind)
            _string(out, name)
            _payload(out, child)
        out += struct.pack(">b", nbt.TAG_END)
    else:
        raise nbt.NbtError(f"cannot write tag type {kind}")


def region_path(directory: Path, chunk_x: int, chunk_z: int) -> Path:
    return directory / f"r.{chunk_x >> 5}.{chunk_z >> 5}.mca"


def read_chunk(directory: Path, chunk_x: int, chunk_z: int) -> tuple[str, Tag]:
    path = region_path(directory, chunk_x, chunk_z)
    for record in anvil.read_chunks(path, chunk_x >> 5, chunk_z >> 5):
        if (record.chunk_x, record.chunk_z) == (chunk_x, chunk_z):
            if record.payload is None:
                raise anvil.AnvilError(f"chunk {chunk_x},{chunk_z}: {record.error}")
            return parse(record.payload)
    raise KeyError(f"chunk {chunk_x},{chunk_z} is not stored in {path}")


def write_chunk(directory: Path, chunk_x: int, chunk_z: int, name: str, root: Tag) -> None:
    """Appends the chunk zlib-compressed in new sectors at the end of the region file and points its location entry
    there; the old sectors stay as unused space, which the format allows."""
    path = region_path(directory, chunk_x, chunk_z)
    data = bytearray(path.read_bytes())
    stored = zlib.compress(serialize(name, root))
    record = struct.pack(">iB", len(stored) + 1, anvil.COMPRESSION_ZLIB) + stored
    sectors = -(-len(record) // anvil.SECTOR_BYTES)
    if sectors > 255:
        raise anvil.AnvilError("a chunk this large would need external storage")
    data += bytes(-len(data) % anvil.SECTOR_BYTES)
    first_sector = len(data) // anvil.SECTOR_BYTES
    data += record + bytes(sectors * anvil.SECTOR_BYTES - len(record))
    index = (chunk_x & 31) + (chunk_z & 31) * anvil.CHUNKS_PER_SIDE
    data[index * 4:index * 4 + 4] = first_sector.to_bytes(3, "big") + bytes([sectors])
    timestamp_offset = anvil.SECTOR_BYTES + index * 4
    data[timestamp_offset:timestamp_offset + 4] = struct.pack(">I", int(time.time()))
    path.write_bytes(bytes(data))
