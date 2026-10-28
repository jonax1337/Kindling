"""A reader for binary NBT (big endian), enough to diff saved worlds.

Compounds become dicts, lists become lists, strings become str, integers become int, floats become float.
Byte, int and long arrays become tuples of int. The tag width of a number is not kept: the comparer asks
whether two worlds hold the same values, not whether they used the same tag type.
"""
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = range(7)
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(7, 13)

_NUMBER_FORMATS = {
    TAG_BYTE: ">b", TAG_SHORT: ">h", TAG_INT: ">i", TAG_LONG: ">q", TAG_FLOAT: ">f", TAG_DOUBLE: ">d",
}
_ARRAY_ELEMENT_CODES = {TAG_BYTE_ARRAY: "b", TAG_INT_ARRAY: "i", TAG_LONG_ARRAY: "q"}


class NbtError(ValueError):
    pass


def parse_root(data: bytes) -> dict:
    """The compound that a chunk file or level.dat holds; its root name is dropped."""
    reader = _Reader(data)
    if reader.unpack(">b") != TAG_COMPOUND:
        raise NbtError("the root tag is not a compound")
    reader.string()
    return reader.payload(TAG_COMPOUND)


class _Reader:
    def __init__(self, data: bytes):
        self._data = data
        self._offset = 0

    def unpack(self, fmt: str):
        try:
            (value,) = struct.unpack_from(fmt, self._data, self._offset)
        except struct.error as error:
            raise NbtError(f"truncated NBT at byte {self._offset}") from error
        self._offset += struct.calcsize(fmt)
        return value

    def take(self, count: int) -> bytes:
        if count < 0 or self._offset + count > len(self._data):
            raise NbtError(f"truncated NBT at byte {self._offset}")
        chunk = self._data[self._offset:self._offset + count]
        self._offset += count
        return chunk

    def string(self) -> str:
        return self.take(self.unpack(">H")).decode("utf-8", errors="surrogateescape")

    def payload(self, tag_type: int):
        if tag_type in _NUMBER_FORMATS:
            return self.unpack(_NUMBER_FORMATS[tag_type])
        if tag_type == TAG_STRING:
            return self.string()
        if tag_type == TAG_LIST:
            return self._list()
        if tag_type == TAG_COMPOUND:
            return self._compound()
        if tag_type in _ARRAY_ELEMENT_CODES:
            return self._array(tag_type)
        raise NbtError(f"unknown tag type {tag_type} at byte {self._offset}")

    def _list(self) -> list:
        element_type = self.unpack(">b")
        length = self.unpack(">i")
        return [self.payload(element_type) for _ in range(length)]

    def _compound(self) -> dict:
        compound = {}
        while True:
            tag_type = self.unpack(">b")
            if tag_type == TAG_END:
                return compound
            name = self.string()
            compound[name] = self.payload(tag_type)

    def _array(self, tag_type: int) -> tuple:
        length = self.unpack(">i")
        code = _ARRAY_ELEMENT_CODES[tag_type]
        raw = self.take(length * struct.calcsize(code))
        return struct.unpack(f">{length}{code}", raw)
