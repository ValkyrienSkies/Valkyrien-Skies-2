#!/usr/bin/env python3
"""
vdex_ship_viewer.py — interactive mouse-orbit viewer for a whole ship,
loaded from a .vdex (ship metadata, NBT) + its accompanying .vdexgeom
(baked per-block-state geometry).

Companion to vdexgeom_render.py and vdexgeom_viewer.py: nothing in either
file is modified. This only imports vdexgeom_render's public pieces
(Geometry, Vertex, VdexGeom, ResourcePack, read_vdexgeom, render_block_state)
and vdexgeom_viewer's `_decimate_geoms` two-tier-render helper, and adds:

  * a small, from-scratch NBT reader/writer (stdlib only — no nbtlib
    dependency), used both to parse the .vdex metadata and to parse the
    ship's vanilla "structure" NBT (Width/Height/Length-less; the
    size/palette/blocks/DataVersion format written by Minecraft's
    structure block / Structure.save()),
  * block-entity NBT fingerprinting that mirrors the Java-side
    nbtFingerprint() (strip x/y/z/Network/Source/Controller/LastKnownPos,
    write as NBT, SHA-256, first 8 bytes as hex) so ship blocks with
    baked per-instance variants (e.g. two identical tanks with different
    contents) resolve to the *correct* baked geometry, not just the first
    one with a matching block state,
  * a whole-ship scene assembler that walks every block in the structure,
    resolves it to `VdexGeom.entries` (falling back through
    `VdexGeom.be_instances` for block-entity blocks), translates its
    baked triangles to the block's position, and merges everything into
    one flat triangle soup,
  * a Tkinter orbit viewer (same left-drag / scroll / double-click
    controls as vdexgeom_viewer.py) that renders that merged soup through
    the existing `render_block_state` rasterizer, reusing the same
    two-tier cheap-preview / background-full-render pipeline so orbiting
    a triangle-heavy ship stays smooth.

Known limitation — block-entity fingerprint order:
    Java's CompoundTag is backed by a HashMap, and nbtFingerprint() writes
    a *copy* of the (stripped) tag, so the byte order NbtIo.write() emits
    depends on Java's HashMap bucket iteration order for that specific set
    of field names, not on the order the fields were originally written.
    This script does not replicate Java's HashMap bucket algorithm; it
    writes fields back out in the order they were read from the structure
    file. This is correct whenever there's no hash-bucket collision among
    a block entity's field names (the common case), but can produce a
    different fingerprint than the bake-time one when a collision occurs.
    Run with --debug-be to print every computed fingerprint and, on a
    miss, the nearest available baked variants for that block, so a
    mismatch is easy to spot. On a miss this script falls back to *some*
    baked variant of the same block-state rather than dropping the block,
    so a ship still renders fully even if one tank shows the wrong skin.

Usage
  python3 vdex_ship_viewer.py path/to/ship.vdex --pack path/to/resourcepack --geom path/to/model.vdexgeom
  python3 vdex_ship_viewer.py path/to/ship.vdex --pack ./assets --geom ./model.vdexgeom --ship "Hull A"
  python3 vdex_ship_viewer.py path/to/ship.vdex --pack ./assets --geom ./model.vdexgeom --debug --debug-be
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import struct
import sys
import threading
import zipfile
import zlib
from dataclasses import dataclass, field

try:
    import tkinter as tk
except ImportError:
    print("error: tkinter is not available in this Python install. "
          "On Debian/Ubuntu: sudo apt install python3-tk", file=sys.stderr)
    sys.exit(1)

from PIL import Image, ImageTk

from vdexgeom_render import (
    Geometry,
    Vertex,
    VdexGeom,
    ResourcePack,
    read_vdexgeom,
    render_block_state,
)

# Reuse the existing preview/full two-tier render tuning + triangle
# decimator instead of duplicating it. Nothing in this module is edited.
import vdexgeom_viewer as _vv


# ============================================================================
# Minimal NBT reader/writer (stdlib only)
# ============================================================================
#
# Compound tags are represented as a plain Python dict, name -> (type_id, value),
# preserving file read order (Python 3.7+ dicts are ordered).
# List tags are represented as (elem_type_id, [values]).
# Scalar payloads (byte/short/int/long/float/double/string) are plain Python
# values. byte_array is `bytes`; int_array/long_array are `list[int]`.

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class _NbtIn:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def _take(self, n: int) -> bytes:
        b = self.data[self.pos:self.pos + n]
        if len(b) != n:
            raise ValueError("truncated NBT data")
        self.pos += n
        return b

    def u8(self) -> int:
        v = struct.unpack_from(">b", self.data, self.pos)[0]
        self.pos += 1
        return v

    def u16(self) -> int:
        v = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i16(self) -> int:
        v = struct.unpack_from(">h", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i32(self) -> int:
        v = struct.unpack_from(">i", self.data, self.pos)[0]
        self.pos += 4
        return v

    def i64(self) -> int:
        v = struct.unpack_from(">q", self.data, self.pos)[0]
        self.pos += 8
        return v

    def f32(self) -> float:
        v = struct.unpack_from(">f", self.data, self.pos)[0]
        self.pos += 4
        return v

    def f64(self) -> float:
        v = struct.unpack_from(">d", self.data, self.pos)[0]
        self.pos += 8
        return v

    def bytes_(self, n: int) -> bytes:
        return self._take(n)

    def string(self) -> str:
        n = self.u16()
        raw = self._take(n)
        # Approximates Java's modified-UTF-8; identical to plain UTF-8 for the
        # ASCII field/identifier names this format actually uses.
        return raw.decode("utf-8", errors="replace")

    def payload(self, type_id: int):
        if type_id == TAG_BYTE:
            return self.u8()
        if type_id == TAG_SHORT:
            return self.i16()
        if type_id == TAG_INT:
            return self.i32()
        if type_id == TAG_LONG:
            return self.i64()
        if type_id == TAG_FLOAT:
            return self.f32()
        if type_id == TAG_DOUBLE:
            return self.f64()
        if type_id == TAG_BYTE_ARRAY:
            n = self.i32()
            return self.bytes_(n)
        if type_id == TAG_STRING:
            return self.string()
        if type_id == TAG_LIST:
            elem_type = self.u8()
            n = self.i32()
            items = [self.payload(elem_type) for _ in range(n)]
            return (elem_type, items)
        if type_id == TAG_COMPOUND:
            return self.compound()
        if type_id == TAG_INT_ARRAY:
            n = self.i32()
            return [self.i32() for _ in range(n)]
        if type_id == TAG_LONG_ARRAY:
            n = self.i32()
            return [self.i64() for _ in range(n)]
        raise ValueError(f"unknown NBT tag id {type_id}")

    def compound(self) -> dict:
        out: dict = {}
        while True:
            t = self.u8()
            if t == TAG_END:
                return out
            name = self.string()
            out[name] = (t, self.payload(t))


class _NbtOut:
    """Mirrors NbtIo.write()'s byte layout, for block-entity fingerprinting."""

    def __init__(self):
        self.buf = bytearray()

    def u8(self, v: int) -> None:
        self.buf += struct.pack(">B", v & 0xFF)

    def i8(self, v: int) -> None:
        self.buf += struct.pack(">b", v)

    def i16(self, v: int) -> None:
        self.buf += struct.pack(">h", v)

    def i32(self, v: int) -> None:
        self.buf += struct.pack(">i", v)

    def i64(self, v: int) -> None:
        self.buf += struct.pack(">q", v)

    def f32(self, v: float) -> None:
        self.buf += struct.pack(">f", v)

    def f64(self, v: float) -> None:
        self.buf += struct.pack(">d", v)

    def string(self, s: str) -> None:
        raw = s.encode("utf-8")
        self.buf += struct.pack(">H", len(raw))
        self.buf += raw

    def payload(self, type_id: int, value) -> None:
        if type_id == TAG_BYTE:
            self.i8(value)
        elif type_id == TAG_SHORT:
            self.i16(value)
        elif type_id == TAG_INT:
            self.i32(value)
        elif type_id == TAG_LONG:
            self.i64(value)
        elif type_id == TAG_FLOAT:
            self.f32(value)
        elif type_id == TAG_DOUBLE:
            self.f64(value)
        elif type_id == TAG_BYTE_ARRAY:
            self.i32(len(value))
            self.buf += bytes(value)
        elif type_id == TAG_STRING:
            self.string(value)
        elif type_id == TAG_LIST:
            elem_type, items = value
            self.u8(elem_type)
            self.i32(len(items))
            for it in items:
                self.payload(elem_type, it)
        elif type_id == TAG_COMPOUND:
            self.compound_body(value)
        elif type_id == TAG_INT_ARRAY:
            self.i32(len(value))
            for v in value:
                self.i32(v)
        elif type_id == TAG_LONG_ARRAY:
            self.i32(len(value))
            for v in value:
                self.i64(v)
        else:
            raise ValueError(f"unknown NBT tag id {type_id}")

    def compound_body(self, compound: dict) -> None:
        for name, (t, v) in compound.items():
            self.u8(t)
            self.string(name)
            self.payload(t, v)
        self.u8(TAG_END)


def _parse_nbt_bytes(raw: bytes) -> dict:
    """Parses a (usually gzipped) blob of NBT bytes and returns its root compound."""
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    elif raw[:4] != b"PK\x03\x04":
        # Don't blind-try zlib on a zip entry: PK-prefixed data is a zip
        # local-file-header, not a zlib stream, and zlib.decompress on it
        # can succeed-but-garbage often enough to be worth excluding explicitly.
        try:
            raw = zlib.decompress(raw)
        except zlib.error:
            pass  # assume already-raw uncompressed NBT
    din = _NbtIn(raw)
    root_type = din.u8()
    if root_type != TAG_COMPOUND:
        raise ValueError(f"root tag is not a compound (got type {root_type}) — "
                         f"first bytes: {raw[:8].hex()}")
    din.string()  # root name, conventionally ""
    return din.compound()


def read_nbt_file(path: str) -> dict:
    """Reads a (usually gzipped) standalone NBT file and returns its root compound."""
    with open(path, "rb") as f:
        raw = f.read()
    try:
        return _parse_nbt_bytes(raw)
    except ValueError as e:
        raise ValueError(f"{path}: {e}") from e


def is_zip_file(path: str) -> bool:
    with open(path, "rb") as f:
        return f.read(4) == b"PK\x03\x04"


def cget(compound: dict, name: str, default=None):
    """Unwraps a compound entry to its bare value (dropping the type id)."""
    entry = compound.get(name)
    return default if entry is None else entry[1]


def ctype(compound: dict, name: str):
    entry = compound.get(name)
    return None if entry is None else entry[0]


# ============================================================================
# Block-entity NBT fingerprinting (mirrors the Java nbtFingerprint())
# ============================================================================

_FINGERPRINT_STRIP_KEYS = {"x", "y", "z", "Network", "Source", "Controller", "LastKnownPos"}


def nbt_fingerprint(nbt: dict) -> str:
    stripped = {k: v for k, v in nbt.items() if k not in _FINGERPRINT_STRIP_KEYS}
    out = _NbtOut()
    out.u8(TAG_COMPOUND)
    out.string("")
    out.compound_body(stripped)
    digest = hashlib.sha256(bytes(out.buf)).digest()
    return digest[:8].hex()


# ============================================================================
# Vanilla structure NBT (size / palette / blocks / DataVersion)
# ============================================================================

@dataclass
class StructureBlock:
    pos: tuple[int, int, int]
    base_key: str          # canonical BlockState key, no NBT suffix
    nbt: dict | None        # raw (unstripped) block-entity compound, if any


@dataclass
class Structure:
    size: tuple[int, int, int]
    blocks: list[StructureBlock] = field(default_factory=list)


def canonical_key(name: str, properties: dict[str, str] | None) -> str:
    if not properties:
        return name
    prop_str = ",".join(f"{k}={properties[k]}" for k in sorted(properties))
    return f"{name}[{prop_str}]"


def parse_structure(root: dict) -> Structure:
    if ctype(root, "size") is None or ctype(root, "palette") is None or ctype(root, "blocks") is None:
        raise ValueError(
            "this NBT file doesn't look like a vanilla structure "
            "(missing 'size'/'palette'/'blocks') — got top-level keys: "
            + ", ".join(root.keys())
        )

    _elem_type, size_vals = cget(root, "size")
    size = (size_vals[0], size_vals[1], size_vals[2])

    _elem_type, palette_entries = cget(root, "palette")
    palette_keys: list[str] = []
    for entry in palette_entries:
        name = cget(entry, "Name", "minecraft:air")
        props_raw = cget(entry, "Properties")
        props = None
        if props_raw is not None:
            props = {k: v for k, (_t, v) in props_raw.items()}
        palette_keys.append(canonical_key(name, props))

    _elem_type, block_entries = cget(root, "blocks")
    blocks: list[StructureBlock] = []
    for entry in block_entries:
        state_idx = cget(entry, "state")
        _pt, pos_vals = cget(entry, "pos")
        pos = (pos_vals[0], pos_vals[1], pos_vals[2])
        nbt = cget(entry, "nbt")  # None if this block has no block entity
        if state_idx is None or state_idx < 0 or state_idx >= len(palette_keys):
            continue
        blocks.append(StructureBlock(pos, palette_keys[state_idx], nbt))

    return Structure(size, blocks)


# ============================================================================
# .vdex ship metadata
# ============================================================================

@dataclass
class ShipEntry:
    name: str
    nbt_file: str
    relative_pos: tuple[float, float, float]
    relative_rot: tuple[float, float, float, float]  # x, y, z, w
    is_static: bool
    scale: float


def _has_nbt_file_field(entry: dict) -> bool:
    return any(t == TAG_STRING and isinstance(v, str) and v.endswith(".nbt")
               for (t, v) in entry.values())


def _find_ships(root: dict, debug: bool = False) -> list[dict]:
    for key in ("ships", "Ships", "SHIPS"):
        if ctype(root, key) == TAG_LIST:
            elem_type, items = cget(root, key)
            if elem_type == TAG_COMPOUND and items:
                if debug:
                    print(f"[vdex] using explicit '{key}' tag for the ship list "
                          f"({len(items)} entries)")
                return items

    def scan(d: dict, path: str):
        for name, (t, v) in d.items():
            if t == TAG_LIST:
                elem_type, items = v
                if elem_type == TAG_COMPOUND and items and all(_has_nbt_file_field(it) for it in items):
                    if debug:
                        print(f"[vdex] auto-detected ship list at '{path}{name}' "
                              f"({len(items)} entries) — every entry has a String "
                              f"field ending in '.nbt'")
                    return items
            elif t == TAG_COMPOUND:
                found = scan(v, path + name + ".")
                if found is not None:
                    return found
        return None

    found = scan(root, "")
    if found is not None:
        return found
    raise ValueError(
        "could not locate a ship list in this .vdex file: looked for an explicit "
        "'ships'/'Ships' tag, then for any List<Compound> whose entries each have "
        "a String field ending in '.nbt'"
    )


def _find_vec(entry: dict, name_candidates: tuple[str, ...], size: int,
             default: tuple[float, ...]) -> tuple[float, ...]:
    for k, (t, v) in entry.items():
        if k.lower() not in name_candidates:
            continue
        if t == TAG_COMPOUND:
            keys = ("x", "y", "z", "w")[:size]
            if all(kk in v for kk in keys):
                return tuple(cget(v, kk) for kk in keys)
        if t == TAG_LIST:
            elem_type, items = v
            if elem_type in (TAG_DOUBLE, TAG_FLOAT) and len(items) == size:
                return tuple(items)
    return default


def _parse_ship_entry(entry: dict) -> ShipEntry:
    nbt_file = next((v for (t, v) in entry.values()
                     if t == TAG_STRING and v.endswith(".nbt")), "ship_0.nbt")
    name = ""
    for k, (t, v) in entry.items():
        if t == TAG_STRING and v != nbt_file:
            name = v
            break
    pos = _find_vec(entry, ("relativepos", "relative_pos", "pos", "position"), 3, (0.0, 0.0, 0.0))
    rot = _find_vec(entry, ("relativerot", "relative_rot", "rot", "rotation"), 4, (0.0, 0.0, 0.0, 1.0))
    is_static = False
    scale = 1.0
    for k, (t, v) in entry.items():
        lk = k.lower()
        if t == TAG_BYTE and "static" in lk:
            is_static = bool(v)
        if t in (TAG_DOUBLE, TAG_FLOAT) and lk == "scale":
            scale = float(v)
    return ShipEntry(name or nbt_file, nbt_file, pos, rot, is_static, scale)


@dataclass
class VdexSource:
    """However the .vdex actually packages its metadata + ship NBTs (a zip
    archive bundling everything as entries, or a bare NBT file with sibling
    *.nbt files on disk), this gives one uniform interface to the rest of
    the script."""
    ships: list[ShipEntry]
    load_structure: object  # Callable[[ShipEntry], Structure]


def _json_vecN(v, keys: tuple[str, ...], default: tuple[float, ...]) -> tuple[float, ...]:
    """Pulls an (x,y,z) / (x,y,z,w) tuple out of a Jackson-serialized
    Vector3dc/Quaterniondc, which could plausibly come out as either a
    {"x":..,"y":..,"z":..} object or a plain [x,y,z] array — accept both."""
    if v is None:
        return default
    if isinstance(v, dict):
        lower = {k.lower(): val for k, val in v.items()}
        try:
            return tuple(float(lower[k]) for k in keys)
        except (KeyError, TypeError, ValueError):
            return default
    if isinstance(v, (list, tuple)) and len(v) == len(keys):
        try:
            return tuple(float(x) for x in v)
        except (TypeError, ValueError):
            return default
    return default


def _ship_entry_from_json(data: dict) -> ShipEntry:
    """Builds a ShipEntry from a Jackson-serialized VdexShipEntry (ships/*.json)."""
    name = data.get("name") or data.get("Name") or ""
    nbt_file = data.get("nbtFile") or data.get("NbtFile") or "ship_0.nbt"
    pos = _json_vecN(data.get("relativePos"), ("x", "y", "z"), (0.0, 0.0, 0.0))
    rot = _json_vecN(data.get("relativeRot"), ("x", "y", "z", "w"), (0.0, 0.0, 0.0, 1.0))
    is_static = bool(data.get("isStatic", False))
    scale = float(data.get("scale", 1.0))
    return ShipEntry(name or nbt_file, nbt_file, pos, rot, is_static, scale)


def _resolve_zip_entry(names: list[str], entry_name: str, *, what: str, path: str) -> str:
    if entry_name in names:
        return entry_name
    base = os.path.basename(entry_name)
    match = next((n for n in names if not n.endswith("/") and os.path.basename(n) == base), None)
    if match is None:
        raise ValueError(f"{what} {entry_name!r} not found in {path}. Zip entries: {names}")
    return match


def _open_vdex_zip(path: str, debug: bool = False) -> VdexSource:
    zf = zipfile.ZipFile(path)
    names = zf.namelist()
    if debug:
        print(f"[vdex] '{path}' is a zip archive with {len(names)} entries: {names}")

    # Observed real layout: metadata.json (VdexMetadata, sans @JsonIgnore'd
    # fields) + one ships/<name>.json per ship (VdexShipEntry) + a
    # structure/ folder of per-ship NBT. Prefer this JSON path whenever a
    # ships/ folder with .json entries is present.
    ship_json_names = sorted(n for n in names
                             if n.startswith("ships/") and n.endswith(".json"))
    if ship_json_names:
        ships: list[ShipEntry] = []
        for n in ship_json_names:
            try:
                data = json.loads(zf.read(n).decode("utf-8"))
            except Exception as e:
                if debug:
                    print(f"[vdex] skipping ship json entry '{n}': {e}")
                continue
            ships.append(_ship_entry_from_json(data))
            if debug:
                print(f"[vdex] loaded ship entry '{n}' -> name={ships[-1].name!r} "
                      f"nbtFile={ships[-1].nbt_file!r}")

        def load_structure(ship: ShipEntry) -> Structure:
            entry_name = _resolve_zip_entry(names, ship.nbt_file,
                                            what=f"ship nbt entry (for ship {ship.name!r})",
                                            path=path)
            return parse_structure(_parse_nbt_bytes(zf.read(entry_name)))

        return VdexSource(ships, load_structure)

    # Fallback: no ships/ folder — try the original assumption that the
    # whole VdexMetadata (ships included) was itself serialized as one NBT
    # entry somewhere in the archive.
    meta_root = None
    meta_name = None
    for name in names:
        try:
            candidate = _parse_nbt_bytes(zf.read(name))
            _find_ships(candidate)
        except Exception:
            continue
        meta_root, meta_name = candidate, name
        break
    if meta_root is None:
        raise ValueError(
            f"couldn't find a ships/*.json folder, and none of the {len(names)} "
            f"entries in {path} look like an NBT-encoded VdexMetadata either. "
            f"Entries: {names}"
        )
    if debug:
        print(f"[vdex] using zip entry '{meta_name}' as the metadata NBT (fallback path)")

    ships = [_parse_ship_entry(e) for e in _find_ships(meta_root, debug=debug)]

    def load_structure(ship: ShipEntry) -> Structure:
        entry_name = _resolve_zip_entry(names, ship.nbt_file,
                                        what=f"ship nbt entry (for ship {ship.name!r})",
                                        path=path)
        return parse_structure(_parse_nbt_bytes(zf.read(entry_name)))

    return VdexSource(ships, load_structure)


def _open_vdex_plain(path: str, ship_dir: str | None, debug: bool = False) -> VdexSource:
    root = read_nbt_file(path)
    ships = [_parse_ship_entry(e) for e in _find_ships(root, debug=debug)]
    directory = ship_dir or os.path.dirname(os.path.abspath(path)) or "."

    def load_structure(ship: ShipEntry) -> Structure:
        nbt_path = os.path.join(directory, ship.nbt_file)
        if not os.path.isfile(nbt_path):
            available = os.listdir(directory) if os.path.isdir(directory) else []
            raise ValueError(f"ship nbt file not found: {nbt_path} "
                             f"(--ship-dir={directory!r}; files there: {available})")
        return parse_structure(read_nbt_file(nbt_path))

    return VdexSource(ships, load_structure)


def open_vdex(path: str, *, ship_dir: str | None = None, debug: bool = False) -> VdexSource:
    if is_zip_file(path):
        return _open_vdex_zip(path, debug=debug)
    return _open_vdex_plain(path, ship_dir, debug=debug)


# ============================================================================
# Ship scene assembly: structure blocks -> translated, merged Geometry list
# ============================================================================

def build_ship_geometry(structure: Structure, geom: VdexGeom, *,
                        debug: bool = False, debug_be: bool = False) -> list[Geometry]:
    entries_by_key: dict[str, list[Geometry]] = dict(geom.entries)
    be_wire_keys = [k for k, _nbt in geom.be_instances]

    all_geoms: list[Geometry] = []
    matched = 0
    be_exact = 0
    be_fallback = 0
    missing: dict[str, int] = {}

    for b in structure.blocks:
        wire_key = b.base_key
        if b.nbt is not None:
            fp = nbt_fingerprint(b.nbt)
            candidate = f"{b.base_key}#{fp}"
            if debug_be:
                print(f"[be] pos={b.pos} base={b.base_key!r} fingerprint={fp} -> {candidate!r}")
            if candidate in entries_by_key:
                wire_key = candidate
                be_exact += 1
            else:
                fallback = next((k for k in be_wire_keys if k.startswith(b.base_key + "#")), None)
                if fallback is not None:
                    wire_key = fallback
                    be_fallback += 1
                    if debug_be:
                        print(f"      no exact match; falling back to {fallback!r}")
                # else: no baked BE variant at all for this block-state — fall
                # through and try the bare base_key below (may still hit a
                # non-BE bake of the same visual block).

        geoms = entries_by_key.get(wire_key)
        if geoms is None and wire_key != b.base_key:
            geoms = entries_by_key.get(b.base_key)
        if geoms is None:
            missing[wire_key] = missing.get(wire_key, 0) + 1
            continue

        matched += 1
        dx, dy, dz = b.pos
        for g in geoms:
            verts = [Vertex(v.x + dx, v.y + dy, v.z + dz,
                            v.r, v.g, v.b, v.a, v.u, v.v, v.nx, v.ny, v.nz)
                    for v in g.vertices]
            all_geoms.append(Geometry(g.render_type, g.texture, g.cull_face, verts))

    if debug:
        print(f"[ship] {matched}/{len(structure.blocks)} blocks resolved "
              f"({be_exact} block-entity exact, {be_fallback} block-entity fallback)")
        if missing:
            print(f"[ship] {sum(missing.values())} blocks had no matching baked geometry:")
            for key, count in sorted(missing.items(), key=lambda kv: -kv[1])[:20]:
                near = [k for k in entries_by_key if k.split("#", 1)[0] == key.split("#", 1)[0]]
                hint = f"  (nearest baked variants: {near[:4]})" if near else "  (block-state not baked at all)"
                print(f"         x{count:<4} {key!r}{hint}")

    return all_geoms


# ============================================================================
# Viewer (Tkinter, two-tier render — same controls as vdexgeom_viewer.py)
# ============================================================================

DEFAULT_YAW = 45.0
DEFAULT_PITCH = 30.0
DEFAULT_ZOOM = 0.35  # ships are much bigger than a single block; start zoomed out


class ShipViewer:
    def __init__(self, ships: list[ShipEntry], structures: list[Structure],
                 geom: VdexGeom, pack: ResourcePack, *,
                 size: int = 512, start_ship: str | None = None,
                 debug: bool = False, debug_be: bool = False):
        self.pack = pack
        self.geom = geom
        self.size = size
        self.debug = debug
        self.debug_be = debug_be
        self.ships = ships
        self.structures = structures

        self.idx = 0
        if start_ship is not None:
            for i, s in enumerate(ships):
                if start_ship in s.name or start_ship in s.nbt_file:
                    self.idx = i
                    break

        self.yaw = DEFAULT_YAW
        self.pitch = DEFAULT_PITCH
        self.zoom = DEFAULT_ZOOM

        self._drag_last: tuple[int, int] | None = None
        self._settle_job: str | None = None
        self._render_generation = 0
        self._tkimg: ImageTk.PhotoImage | None = None
        self._scene_cache: dict[int, list[Geometry]] = {}
        self._preview_cache: dict[int, list[Geometry]] = {}

        self._build_ui()
        self._render_full_blocking()

    # -- scene building (cached per ship) ----------------------------------
    def _scene_geoms(self) -> list[Geometry]:
        cached = self._scene_cache.get(self.idx)
        if cached is not None:
            return cached
        built = build_ship_geometry(self.structures[self.idx], self.geom,
                                    debug=self.debug, debug_be=self.debug_be)
        self._scene_cache[self.idx] = built
        return built

    def _preview_geoms(self) -> list[Geometry]:
        cached = self._preview_cache.get(self.idx)
        if cached is not None:
            return cached
        decimated = _vv._decimate_geoms(self._scene_geoms(), _vv._PREVIEW_MAX_TRIS)
        self._preview_cache[self.idx] = decimated
        return decimated

    # -- UI ------------------------------------------------------------------
    def _build_ui(self) -> None:
        self.root = tk.Tk()
        self.root.title("vdex ship viewer")
        self.root.configure(bg="#141416")

        left = tk.Frame(self.root, bg="#141416")
        left.pack(side=tk.LEFT, fill=tk.Y)

        list_scroll = tk.Scrollbar(left)
        list_scroll.pack(side=tk.RIGHT, fill=tk.Y)

        self.listbox = tk.Listbox(left, width=32, activestyle="none",
                                  bg="#1c1c20", fg="#e6e6e6",
                                  selectbackground="#3a6ea5",
                                  yscrollcommand=list_scroll.set,
                                  exportselection=False)
        for s in self.ships:
            self.listbox.insert(tk.END, s.name)
        self.listbox.selection_set(self.idx)
        self.listbox.activate(self.idx)
        self.listbox.see(self.idx)
        self.listbox.pack(side=tk.LEFT, fill=tk.Y)
        list_scroll.config(command=self.listbox.yview)
        self.listbox.bind("<<ListboxSelect>>", self._on_select_ship)

        right = tk.Frame(self.root, bg="#141416")
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        self.image_label = tk.Label(right, bg="#141416", cursor="fleur")
        self.image_label.pack(side=tk.TOP)
        self.image_label.bind("<ButtonPress-1>", self._on_press)
        self.image_label.bind("<B1-Motion>", self._on_drag)
        self.image_label.bind("<ButtonRelease-1>", self._on_release)
        self.image_label.bind("<Double-Button-1>", self._on_reset)
        self.image_label.bind("<MouseWheel>", self._on_wheel)
        self.image_label.bind("<Button-4>", lambda e: self._zoom_by(1.1))
        self.image_label.bind("<Button-5>", lambda e: self._zoom_by(1 / 1.1))

        self.status = tk.Label(right, anchor="w", justify=tk.LEFT,
                               bg="#141416", fg="#9a9a9a",
                               font=("TkDefaultFont", 9))
        self.status.pack(side=tk.TOP, fill=tk.X, padx=6, pady=(4, 6))

        self.root.bind("<Left>", lambda e: self._step_ship(-1))
        self.root.bind("<Right>", lambda e: self._step_ship(1))
        self.root.bind("[", lambda e: self._step_ship(-1))
        self.root.bind("]", lambda e: self._step_ship(1))
        self.root.bind("r", lambda e: self._on_reset(None))

    # -- mouse handlers --------------------------------------------------------
    def _on_press(self, event) -> None:
        self._drag_last = (event.x, event.y)

    def _on_release(self, event) -> None:
        self._drag_last = None

    def _on_drag(self, event) -> None:
        if self._drag_last is None:
            self._drag_last = (event.x, event.y)
            return
        lx, ly = self._drag_last
        dx, dy = event.x - lx, event.y - ly
        self._drag_last = (event.x, event.y)
        self.yaw = (self.yaw - dx * 0.5) % 360.0
        self.pitch = max(-89.0, min(89.0, self.pitch + dy * 0.5))
        self._interaction_changed()

    def _on_wheel(self, event) -> None:
        factor = 1.1 if event.delta > 0 else 1 / 1.1
        self._zoom_by(factor)

    def _zoom_by(self, factor: float) -> None:
        self.zoom = max(0.02, min(8.0, self.zoom * factor))
        self._interaction_changed()

    def _on_reset(self, _event) -> None:
        self.yaw, self.pitch, self.zoom = DEFAULT_YAW, DEFAULT_PITCH, DEFAULT_ZOOM
        self._interaction_changed()

    def _on_select_ship(self, _event) -> None:
        sel = self.listbox.curselection()
        if not sel:
            return
        self.idx = sel[0]
        self._interaction_changed()

    def _step_ship(self, delta: int) -> None:
        self.idx = (self.idx + delta) % len(self.ships)
        self.listbox.selection_clear(0, tk.END)
        self.listbox.selection_set(self.idx)
        self.listbox.see(self.idx)
        self._interaction_changed()

    # -- two-tier rendering (mirrors vdexgeom_viewer.Viewer) ------------------
    def _interaction_changed(self) -> None:
        self._render_preview()
        if self._settle_job is not None:
            self.root.after_cancel(self._settle_job)
        self._settle_job = self.root.after(_vv._SETTLE_DELAY_MS, self._start_full_render)

    def _internal_size_for(self, display_size: int) -> int:
        return max(16, int(round(display_size / self.zoom)))

    def _render_preview(self) -> None:
        geoms = self._preview_geoms()
        preview_side = max(_vv._PREVIEW_MIN_SIDE,
                           min(_vv._PREVIEW_MAX_SIDE,
                               int(round(self._internal_size_for(self.size) * _vv._PREVIEW_SCALE))))
        img = render_block_state(geoms, self.pack, canvas_size=preview_side,
                                 yaw=self.yaw, pitch=self.pitch)
        img = img.resize((self.size, self.size), Image.NEAREST)
        self._show_image(img, refining=True)

    def _start_full_render(self) -> None:
        self._settle_job = None
        self._render_generation += 1
        gen = self._render_generation

        geoms = self._scene_geoms()
        yaw, pitch, size = self.yaw, self.pitch, self.size
        internal_size = self._internal_size_for(size)

        def work() -> None:
            img = render_block_state(geoms, self.pack, canvas_size=internal_size,
                                     yaw=yaw, pitch=pitch)
            if internal_size != size:
                resample = Image.NEAREST if internal_size < size else Image.BOX
                img = img.resize((size, size), resample)
            self.root.after(0, lambda: self._apply_full_render(gen, img))

        threading.Thread(target=work, daemon=True).start()

    def _apply_full_render(self, gen: int, img: Image.Image) -> None:
        if gen != self._render_generation:
            return
        self._show_image(img, refining=False)

    def _render_full_blocking(self) -> None:
        self._render_generation += 1
        self._render_preview()
        self._start_full_render()

    def _show_image(self, img: Image.Image, *, refining: bool) -> None:
        self._tkimg = ImageTk.PhotoImage(img)
        self.image_label.configure(image=self._tkimg)
        self._update_status(refining=refining)

    def _update_status(self, *, refining: bool) -> None:
        ship = self.ships[self.idx]
        n_tris = sum(len(g.vertices) // 3 for g in self._scene_geoms())
        tag = "  (refining…)" if refining else ""
        self.status.configure(
            text=(f"{ship.name}{tag}\n"
                  f"yaw {self.yaw:6.1f}°   pitch {self.pitch:6.1f}°   "
                  f"zoom {self.zoom:5.3f}x   {n_tris} tris   "
                  f"ship {self.idx + 1}/{len(self.ships)}   "
                  f"(drag=orbit, wheel=zoom, dbl-click/r=reset, ←/→=switch)")
        )

    def run(self) -> None:
        self.root.mainloop()


# ============================================================================
# CLI
# ============================================================================

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="Load a .vdex ship + its .vdexgeom and orbit the assembled ship with the mouse.")
    ap.add_argument("vdex", help="path to a .vdex file")
    ap.add_argument("--geom", required=True, help="path to the accompanying .vdexgeom file")
    ap.add_argument("--pack", action="append", default=[], metavar="DIR",
                    help="resource-pack root containing assets/<ns>/textures/... (repeatable)")
    ap.add_argument("--ship-dir", default=None,
                    help="directory containing each ship's *.nbt file, when .vdex is a bare NBT "
                         "file rather than a zip archive (default: the .vdex file's directory; "
                         "ignored if .vdex is a zip — ship NBTs are read from inside it instead)")
    ap.add_argument("--ship", default=None,
                    help="open with this ship selected initially (substring match on name or filename)")
    ap.add_argument("--size", type=int, default=512, help="viewport size in px (default: 512)")
    ap.add_argument("--debug", action="store_true",
                    help="print ship/tag auto-detection and per-block resolution stats")
    ap.add_argument("--debug-be", action="store_true",
                    help="print every computed block-entity fingerprint and its match")
    args = ap.parse_args(argv)

    if not args.pack:
        print("error: supply at least one --pack <dir> (a resource-pack root with assets/...)",
              file=sys.stderr)
        return 2

    pack = ResourcePack(args.pack)
    geom = read_vdexgeom(args.geom)
    print(f"loaded {args.geom}: {len(geom.texture_palette)} textures, "
          f"{len(geom.render_type_palette)} render types, {len(geom.entries)} block-state entries, "
          f"{len(geom.be_instances)} block-entity instances")

    try:
        source = open_vdex(args.vdex, ship_dir=args.ship_dir, debug=args.debug)
    except Exception as e:
        print(f"error: failed to open {args.vdex}: {e}", file=sys.stderr)
        return 1
    ships = source.ships
    if not ships:
        print(f"error: no ships found in {args.vdex}", file=sys.stderr)
        return 1
    print(f"loaded {args.vdex}: {len(ships)} ship(s): " + ", ".join(s.name for s in ships))

    structures: list[Structure] = []
    for s in ships:
        try:
            structure = source.load_structure(s)
        except Exception as e:
            print(f"error: couldn't load structure for ship {s.name!r}: {e}", file=sys.stderr)
            return 1
        structures.append(structure)
        print(f"  {s.name}: {s.nbt_file} -> size {structure.size}, {len(structure.blocks)} blocks")

    viewer = ShipViewer(ships, structures, geom, pack, size=args.size,
                        start_ship=args.ship, debug=args.debug, debug_be=args.debug_be)
    viewer.run()
    return 0


if __name__ == "__main__":
    sys.exit(main())