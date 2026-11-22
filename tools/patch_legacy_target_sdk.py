#!/usr/bin/env python3
"""Set an APK's declared SDK values to legacy-compatible values.

This narrowly updates the typed integer values for uses-sdk attributes in
Android's binary XML manifest. It does not alter DEX or app resources.
"""

from __future__ import annotations

import argparse
import struct
import zipfile
from pathlib import Path


NO_INDEX = 0xFFFFFFFF
UTF8_FLAG = 0x00000100
RES_STRING_POOL_TYPE = 0x0001
RES_XML_START_ELEMENT_TYPE = 0x0102
TYPE_INT_DEC = 0x10


def _read_length8(data: bytes, offset: int) -> tuple[int, int]:
    first = data[offset]
    if first & 0x80:
        return (((first & 0x7F) << 8) | data[offset + 1], offset + 2)
    return first, offset + 1


def _read_length16(data: bytes, offset: int) -> tuple[int, int]:
    first = struct.unpack_from("<H", data, offset)[0]
    if first & 0x8000:
        second = struct.unpack_from("<H", data, offset + 2)[0]
        return (((first & 0x7FFF) << 16) | second, offset + 4)
    return first, offset + 2


def _parse_string_pool(data: bytes, start: int, size: int) -> list[str]:
    string_count, style_count, flags, strings_start, _styles_start = struct.unpack_from(
        "<IIIII", data, start + 8
    )
    offsets_start = start + 28
    offsets = [
        struct.unpack_from("<I", data, offsets_start + 4 * i)[0]
        for i in range(string_count)
    ]
    strings_base = start + strings_start
    result: list[str] = []
    for relative in offsets:
        pos = strings_base + relative
        if flags & UTF8_FLAG:
            _utf16_len, pos = _read_length8(data, pos)
            byte_len, pos = _read_length8(data, pos)
            result.append(data[pos : pos + byte_len].decode("utf-8"))
        else:
            _utf16_len, pos = _read_length16(data, pos)
            byte_len = _utf16_len * 2
            result.append(data[pos : pos + byte_len].decode("utf-16le"))
    return result


def patch_manifest(data: bytes) -> bytes:
    if len(data) < 8 or struct.unpack_from("<H", data, 0)[0] != 0x0003:
        raise ValueError("AndroidManifest.xml is not binary XML")
    declared_size = struct.unpack_from("<I", data, 4)[0]
    if declared_size != len(data):
        raise ValueError("binary XML size header does not match file length")

    strings: list[str] | None = None
    pos = struct.unpack_from("<H", data, 2)[0]
    out = bytearray(data)
    patched: set[str] = set()
    expected_patches = {"minSdkVersion", "targetSdkVersion", "versionCode"}
    while pos < len(data):
        chunk_type, header_size, chunk_size = struct.unpack_from("<HHI", data, pos)
        if chunk_size < header_size or pos + chunk_size > len(data):
            raise ValueError(f"invalid XML chunk at offset {pos}")
        if chunk_type == RES_STRING_POOL_TYPE:
            strings = _parse_string_pool(data, pos, chunk_size)
        elif chunk_type == RES_XML_START_ELEMENT_TYPE and strings is not None:
            element_name_index = struct.unpack_from("<I", data, pos + 20)[0]
            element_name = strings[element_name_index]
            if element_name in ("manifest", "uses-sdk"):
                attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, pos + 24)
                attrs = pos + 16 + attr_start
                if attr_size < 20:
                    raise ValueError("unexpected binary XML attribute size")
                for i in range(attr_count):
                    attr = attrs + i * attr_size
                    name_index = struct.unpack_from("<I", data, attr + 4)[0]
                    name = strings[name_index]
                    values = (
                        {"versionCode": 35}
                        if element_name == "manifest"
                        else {"minSdkVersion": 24, "targetSdkVersion": 24}
                    )
                    if name not in values:
                        continue
                    value_type = data[attr + 15]
                    if value_type not in (TYPE_INT_DEC, 0x11):
                        raise ValueError(f"{name} is not encoded as an integer")
                    struct.pack_into("<I", out, attr + 16, values[name])
                    struct.pack_into("<I", out, attr + 8, NO_INDEX)
                    patched.add(name)
        pos += chunk_size

    if patched != expected_patches:
        raise ValueError(f"expected {sorted(expected_patches)}, patched only {sorted(patched)}")
    return bytes(out)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()

    with zipfile.ZipFile(args.source, "r") as source:
        entries = [(info, source.read(info.filename)) for info in source.infolist()]

    found_manifest = False
    with zipfile.ZipFile(args.destination, "w") as destination:
        for info, data in entries:
            if info.filename == "AndroidManifest.xml":
                data = patch_manifest(data)
                found_manifest = True
            if info.filename.upper().startswith("META-INF/") and info.filename.upper().endswith(
                (".RSA", ".DSA", ".EC", ".SF", "MANIFEST.MF")
            ):
                continue
            destination.writestr(info, data)

    if not found_manifest:
        args.destination.unlink(missing_ok=True)
        raise ValueError("APK has no AndroidManifest.xml")
    print(f"Wrote legacy-target manifest APK: {args.destination}")


if __name__ == "__main__":
    main()
