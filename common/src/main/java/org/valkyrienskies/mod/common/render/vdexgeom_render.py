#!/usr/bin/env python3
"""
vdexgeom_render.py — standalone renderer for VS2's .vdexgeom baked-geometry files.

Reads a .vdexgeom produced by org.valkyrienskies.mod.common.render.BakedGeometrySerializer
and renders each block-state palette entry as a textured image by painting the
referenced Minecraft textures onto the file's existing quads.

It is a faithful *non-JVM consumer* of that format: the read path mirrors
BakedGeometrySerializer byte-for-byte (GZIP -> big-endian DataInputStream, length-
prefixed standard UTF-8 strings, palette-deduplicated texture/render-type indices,
fixed per-vertex field layout). No Mojang types are reconstructed; strings stay
strings, exactly as the serializer's comments prescribe.

Format version 2: every Geometry.vertices list is now a flat, independent triangle
list — len(vertices) is always a multiple of 3, and every consecutive group of 3 is
one triangle sharing no vertices with any other triangle. Earlier revisions wrote
whatever grouping the source draw call happened to use (usually a 4-vertex quad,
occasionally a whole multi-primitive buffer as one opaque group for block entities)
and left the reader to guess where primitive boundaries fell; that guesswork -
and the quad/fan-slicing code it required here - is gone as of this version. The
Java side (Bakery) is responsible for expanding QUADS/TRIANGLES/TRIANGLE_STRIP/
TRIANGLE_FAN into flat triangles before anything is written; this reader just
consumes 3 vertices at a time.

Format version 3: a trailing section after the geometry-entries table carries each
BlockEntity's own NBT (BlockEntity.saveWithFullMetadata), one record per actual
instance. Each record's key is the same "wire key" the geometry palette uses for that
entry — the plain canonical BlockState string for non-BE blocks, or
canonicalKey(state)+"#"+nbtFingerprint(nbt) for BE blocks (so two same-state tanks
with different fluid/level land in distinct palette entries, each with its own
BlockEntityRenderer-baked geometry). Per-instance (not deduped per state): two chests
share the state part of the key but differ in the fingerprint part and the carried NBT.
Each tag uses vanilla NbtIo framing (self-delimiting) decoded by the inline NBT reader
above read_vdexgeom. This script reads the NBT into VdexGeom.be_instances so it's
available; it does NOT add NBT-driven rendering (drawing a chest's contents, a sign's
text) — that's a separate task.

Textures are resolved the way Minecraft itself loads them: a palette entry like
"minecraft:block/diorite" becomes assets/minecraft/textures/block/diorite.png,
searched across one or more unpacked resource-pack roots given via --pack.

Dependencies: numpy, Pillow, (matplotlib only for the contact sheet). No GPU/GL.
"""

import argparse
import gzip
import io
import os
import struct
import sys
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

# --------------------------------------------------------------------------- #
# Binary format constants — must match BakedGeometrySerializer.java exactly.
# --------------------------------------------------------------------------- #
MAGIC = 0x56444558  # "VDEX"
FORMAT_VERSION = 3
DIRECTION_NAMES = ["DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"]


# --------------------------------------------------------------------------- #
# Inline NBT reader: mirrors NbtIo.read() for the v3 BlockEntity-NBT section.
# NBT is a simple recursive TLV — tag-type byte, then (except for TAG_END) a
# name length+bytes, then a type-specific payload. This covers every type the
# BlockEntity tags actually use; no external nbtlib dependency.
# --------------------------------------------------------------------------- #
_NBT_END = 0
_NBT_BYTE = 1
_NBT_SHORT = 2
_NBT_INT = 3
_NBT_LONG = 4
_NBT_FLOAT = 5
_NBT_DOUBLE = 6
_NBT_BYTE_ARRAY = 7
_NBT_STRING = 8
_NBT_LIST = 9
_NBT_COMPOUND = 10
_NBT_INT_ARRAY = 11
_NBT_LONG_ARRAY = 12


def _read_nbt_payload(din: "_DataIn", tag_type: int):
    if tag_type == _NBT_BYTE:
        return struct.unpack(">b", din._take(1))[0]
    if tag_type == _NBT_SHORT:
        return struct.unpack(">h", din._take(2))[0]
    if tag_type == _NBT_INT:
        return struct.unpack(">i", din._take(4))[0]
    if tag_type == _NBT_LONG:
        return struct.unpack(">q", din._take(8))[0]
    if tag_type == _NBT_FLOAT:
        return struct.unpack(">f", din._take(4))[0]
    if tag_type == _NBT_DOUBLE:
        return struct.unpack(">d", din._take(8))[0]
    if tag_type == _NBT_BYTE_ARRAY:
        n = struct.unpack(">i", din._take(4))[0]
        return din._take(n)
    if tag_type == _NBT_STRING:
        # NBT strings are length-prefixed modified-UTF-8. BE field names and most
        # values are ASCII, so plain UTF-8 decode is correct in practice; rare
        # modified-UTF-8 sequences (null as 0xC0 0x80, supplementary chars) are
        # a non-issue for this dev script.
        n = struct.unpack(">H", din._take(2))[0]
        return din._take(n).decode("utf-8")
    if tag_type == _NBT_LIST:
        elem_type = din.read_byte()
        n = struct.unpack(">i", din._take(4))[0]
        return [_read_nbt_payload(din, elem_type) for _ in range(n)]
    if tag_type == _NBT_COMPOUND:
        result: dict = {}
        while True:
            child_type = din.read_byte()
            if child_type == _NBT_END:
                break
            name_len = struct.unpack(">H", din._take(2))[0]
            name = din._take(name_len).decode("utf-8")
            result[name] = _read_nbt_payload(din, child_type)
        return result
    if tag_type == _NBT_INT_ARRAY:
        n = struct.unpack(">i", din._take(4))[0]
        return [struct.unpack(">i", din._take(4))[0] for _ in range(n)]
    if tag_type == _NBT_LONG_ARRAY:
        n = struct.unpack(">i", din._take(4))[0]
        return [struct.unpack(">q", din._take(8))[0] for _ in range(n)]
    raise ValueError(f"unknown NBT tag type {tag_type}")


def _read_nbt(din: "_DataIn") -> dict:
    """Reads one CompoundTag exactly as NbtIo.write(CompoundTag, ...) wrote it:
    a TAG_COMPOUND type byte, a (normally empty) name, then the compound payload.
    Returns the payload as a dict."""
    tag_type = din.read_byte()
    name_len = struct.unpack(">H", din._take(2))[0]
    _root_name = din._take(name_len).decode("utf-8")  # normally empty
    return _read_nbt_payload(din, tag_type)


# --------------------------------------------------------------------------- #
# Reader: mirrors BakedGeometrySerializer.read() / readGeometry() line-for-line.
# --------------------------------------------------------------------------- #
class _DataIn:
    """Thin big-endian reader standing in for java.io.DataInputStream."""

    __slots__ = ("buf", "pos")

    def __init__(self, raw: bytes):
        self.buf = raw
        self.pos = 0

    def _take(self, n: int) -> bytes:
        b = self.buf[self.pos:self.pos + n]
        if len(b) != n:
            raise EOFError("unexpected end of vdexgeom stream")
        self.pos += n
        return b

    def read_int(self) -> int:
        return struct.unpack(">i", self._take(4))[0]

    def read_byte(self) -> int:  # unsigned 0..255
        return self._take(1)[0]

    def read_short(self) -> int:  # unsigned 0..65535 (overlay/lightmap are ignored anyway)
        return struct.unpack(">H", self._take(2))[0]

    def read_float(self) -> float:
        return struct.unpack(">f", self._take(4))[0]

    def read_string(self) -> str:
        # length-prefixed *standard* UTF-8 (int length + raw bytes), NOT Java's
        # modified UTF-8 — this is the one detail the serializer calls out as
        # load-bearing for non-JVM readers.
        n = self.read_int()
        return self._take(n).decode("utf-8")


@dataclass
class Vertex:
    x: float
    y: float
    z: float
    r: int
    g: int
    b: int
    a: int
    u: float
    v: float
    # overlay/lightmap are carried by the format but irrelevant for an unlit
    # texture preview, so they're parsed and dropped.
    nx: float
    ny: float
    nz: float


@dataclass
class Geometry:
    render_type: str
    texture: str | None      # ResourceLocation string, e.g. "minecraft:block/diorite"
    cull_face: str | None    # one of DIRECTION_NAMES, or None
    vertices: list           # list[Vertex]; always a flat triangle list (len % 3 == 0) as of v2


@dataclass
class VdexGeom:
    texture_palette: list[str]
    render_type_palette: list[str]
    # canonical BlockState key (see BakedGeometrySerializer.BlockStateKeys.canonicalKey)
    # -> list of geometries for that state. Insertion-ordered like the writer.
    entries: "list[tuple[str, list[Geometry]]]" = field(default_factory=list)
    # v3: per-instance BlockEntity NBT (one record per actual BlockEntity in the ship). The first
    # element is the palette wire key — the plain canonical BlockState string for non-BE blocks,
    # or canonicalKey(state)+"#"+nbtFingerprint(nbt) for BE blocks (so same-state BEs with different
    # NBT land in distinct palette entries). A consumer joins be_instances[i][0] -> entries[j] to
    # attach NBT to the rendered model. Multiple records may share the same wire key (two identical
    # tanks at different positions). Empty if the ship has no block entities.
    be_instances: "list[tuple[str, dict]]" = field(default_factory=list)


def read_vdexgeom(path: str) -> VdexGeom:
    with open(path, "rb") as f:
        raw = gzip.decompress(f.read())

    din = _DataIn(raw)
    magic = din.read_int()
    if magic != MAGIC:
        raise ValueError(f"bad magic 0x{magic:08X}, not a vdexgeom file")
    version = din.read_int()
    if version != FORMAT_VERSION:
        raise ValueError(f"unsupported vdexgeom format version {version} (expected {FORMAT_VERSION})")

    texture_palette = [din.read_string() for _ in range(din.read_int())]
    render_type_palette = [din.read_string() for _ in range(din.read_int())]

    entries: list[tuple[str, list[Geometry]]] = []
    for _ in range(din.read_int()):
        key = din.read_string()
        geoms: list[Geometry] = []
        for _ in range(din.read_int()):
            geoms.append(_read_geometry(din, texture_palette, render_type_palette))
        entries.append((key, geoms))

    # v3: trailing per-instance BlockEntity-NBT section. A reader wanting only models
    # could stop after entries; this reads the section so the NBT is available.
    be_instances: list[tuple[str, dict]] = []
    for _ in range(din.read_int()):
        canonical_key = din.read_string()
        nbt = _read_nbt(din)
        be_instances.append((canonical_key, nbt))

    return VdexGeom(texture_palette, render_type_palette, entries, be_instances)


def _read_geometry(din: _DataIn, textures: list[str], render_types: list[str]) -> Geometry:
    render_type = render_types[din.read_int()]

    tex_index = din.read_int()
    texture = textures[tex_index] if tex_index >= 0 else None

    cull_ordinal = din.read_byte()
    # Java writes writeByte(ordinal) or writeByte(-1) for "none"; -1 becomes 0xFF (255).
    cull_face = DIRECTION_NAMES[cull_ordinal] if cull_ordinal < 6 else None

    vert_count = din.read_int()
    if vert_count % 3 != 0:
        raise ValueError(
            f"vdexgeom vertex count {vert_count} is not a multiple of 3 "
            f"(expected a flat triangle list) for render type {render_type!r}"
        )

    verts: list[Vertex] = []
    for _ in range(vert_count):
        x, y, z = din.read_float(), din.read_float(), din.read_float()
        r, g, b, a = din.read_byte(), din.read_byte(), din.read_byte(), din.read_byte()
        u, v = din.read_float(), din.read_float()
        # 4 shorts of overlay/lightmap coords — parsed to keep the stream aligned,
        # but unused by this renderer.
        din.read_short(); din.read_short(); din.read_short(); din.read_short()
        nx, ny, nz = din.read_float(), din.read_float(), din.read_float()
        verts.append(Vertex(x, y, z, r, g, b, a, u, v, nx, ny, nz))

    return Geometry(render_type, texture, cull_face, verts)


# --------------------------------------------------------------------------- #
# Resource pack: namespace:path -> assets/<namespace>/textures/<path>.png
# --------------------------------------------------------------------------- #
class ResourcePack:
    """
    Resolves a baked texture ResourceLocation ("namespace:path") to a PNG by
    walking one or more unpacked resource-pack roots in order, exactly mirroring
    Minecraft's own asset lookup. First hit wins; results are cached.
    """

    def __init__(self, roots: list[str]):
        self.roots = [os.path.abspath(r) for r in roots]
        self._cache: dict[str, Image.Image | None] = {}

    def resolve_path(self, resloc: str) -> str | None:
        if ":" not in resloc:
            return None
        namespace, path = resloc.split(":", 1)
        rel = os.path.join("assets", namespace, "textures", f"{path}.png")
        for root in self.roots:
            candidate = os.path.join(root, rel)
            if os.path.isfile(candidate):
                return candidate
        return None

    def get(self, resloc: str) -> Image.Image | None:
        if resloc in self._cache:
            return self._cache[resloc]
        p = self.resolve_path(resloc)
        img = None
        if p is not None:
            try:
                img = Image.open(p).convert("RGBA")
                w, h = img.size
                # Animated block textures (lava_still, water_still, etc.) are a vertical strip of
                # square frames, with a sibling .mcmeta controlling which strip index plays when --
                # frame order there does NOT have to match the strip's raw top-to-bottom order, and
                # for lava several strip frames are deliberately dark/ember-heavy. Cropping to "frame
                # 0 of the strip" can land on one of those and render as solid black. This renderer
                # has no animation clock, so average every frame into one representative texture
                # instead of gambling on picking a single (possibly dark) frame.
                if h > w and h % w == 0:
                    n_frames = h // w
                    arr = np.asarray(img, dtype=np.float64).reshape(n_frames, w, w, 4)
                    avg = arr.mean(axis=0)
                    img = Image.fromarray(np.clip(avg, 0, 255).astype(np.uint8), "RGBA")
            except Exception as e:
                print(f"  warn: failed to open {p}: {e}", file=sys.stderr)
                img = None
        self._cache[resloc] = img
        return img


def _checker(w: int, h: int) -> Image.Image:
    """Magenta/black checker for missing textures — loud, never silent."""
    img = Image.new("RGBA", (w, h), (255, 0, 255, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if ((x // 8) + (y // 8)) % 2 == 0:
                px[x, y] = (0, 0, 0, 255)
    return img


# --------------------------------------------------------------------------- #
# Software rasterizer: texture each triangle, composite into one image per entry.
# --------------------------------------------------------------------------- #
def _rot_matrix(yaw_deg: float, pitch_deg: float) -> np.ndarray:
    """Yaw about Y then pitch about X — a camera-relative rotation of the model."""
    yaw, pitch = np.radians(yaw_deg), np.radians(pitch_deg)
    cy, sy = np.cos(yaw), np.sin(yaw)
    cp, sp = np.cos(pitch), np.sin(pitch)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]], dtype=np.float64)
    return rx @ ry


def _raw_uv(verts: list[Vertex]) -> np.ndarray:
    """
    Returns each vertex's stored (u, v) clipped to [0, 1].

    The producer (Bakery.bakeQuad / splitByTexture) un-bakes each vertex's atlas
    UV into sprite-local [0,1] space via (uv - sprite.U0) / (sprite.U1 - sprite.U0)
    before writing. A correctly un-baked vertex therefore always lands in [0,1];
    anything outside is float error or a producer bug (see the note below). Clip
    to [0,1] so such stray values sample a valid edge texel rather than wrapping
    around the texture — wrapping would silently manufacture a wrong tile out of
    garbage data and is NOT what the format means by these coordinates.

    NOTE on out-of-range values seen in the wild: Bakery.splitByTexture resolves
    a triangle's sprite from v2's UV only, then applies that single sprite's box
    to v0/v1 via unbakeUv. If a quad straddles an atlas seam, v0/v1 belong to a
    *different* sprite than v2, and unbaking them against v2's sprite yields
    coordinates far outside [0,1] (e.g. 1.75, -0.25). That is a producer defect
    — the right fix is in Bakery (per-vertex sprite resolution), not here. We
    clip so the renderer degrades gracefully (edge texel) instead of crashing or
    wrapping into nonsense; once the producer is fixed these clips become inert.
    """
    for w in verts:
        if w.u < 0 or w.u > 1 or w.v < 0 or w.v > 1:
            print(f"  warn: vertex UV out of range: ({w.u:.3f}, {w.v:.3f})", file=sys.stderr)
    u = np.clip(np.array([w.u for w in verts], dtype=np.float64), 0.0, 1.0)
    v = np.clip(np.array([w.v for w in verts], dtype=np.float64), 0.0, 1.0)
    return np.column_stack([u, v])


_MAX_MIP = 4  # don't build chains smaller than 1x1 / 16 levels
_MIP_CACHE: dict[int, list[np.ndarray]] = {}


def _mip_chain(tex_rgba: np.ndarray) -> list[np.ndarray]:
    """
    Returns [level0, level1, ...] where each level is the previous averaged 2x2.
    level0 is the original texture. Memoized by array identity — a given geometry
    reuses one tex_rgba array across its triangles, and the chain is tiny (a 16x16
    texture's full chain is ~1.4 KB), so caching by id() is safe and cheap.
    """
    key = id(tex_rgba)
    cached = _MIP_CACHE.get(key)
    if cached is not None:
        return cached
    chain = [tex_rgba]
    tex = tex_rgba
    for _ in range(_MAX_MIP):
        h, w = tex.shape[:2]
        if w < 2 or h < 2:
            break
        # Drop a trailing odd row/col so the 2x2 box average tiles evenly.
        tex = tex[:h - h % 2, :w - w % 2].reshape(h // 2, 2, w // 2, 2, 4).mean(axis=(1, 3))
        chain.append(tex)
    _MIP_CACHE[key] = chain
    return chain


def _sample_texture(tex_rgba: np.ndarray, uv: np.ndarray, mip_level: int) -> np.ndarray:
    if mip_level == 0:
        tex = tex_rgba
    else:
        chain = _mip_chain(tex_rgba)
        tex = chain[min(mip_level, len(chain) - 1)]
    h, w = tex.shape[:2]
    px = np.clip((uv[:, 0] * w).astype(np.int32), 0, w - 1)
    py = np.clip((uv[:, 1] * h).astype(np.int32), 0, h - 1)
    return tex[py, px]


def _mip_for_triangle(screen: np.ndarray, uv: np.ndarray, tex_w: int, tex_h: int) -> int:
    """
    Picks a mip level for a triangle by comparing its texture footprint to its
    screen footprint — the standard "texels per pixel" heuristic Minecraft's
    sampler uses. Returns floor(log2(max compression axis)) clamped to [0, _MAX_MIP].

    A face drawn large on screen is magnified -> ratio < 1 -> mip 0 (full-res).
    Only genuine minification (texture smaller on screen than its native size)
    selects a higher level. This is what makes cutout_mipped blocks render at
    full 16x16 up close instead of looking like an 8x8.
    """
    sx = np.ptp(screen[:, 0])
    sy = np.ptp(screen[:, 1])
    s_span = max(sx, sy, 1e-6)
    # How many texture pixels map across the triangle's longest screen side.
    ux = np.ptp(uv[:, 0]) * tex_w
    uy = np.ptp(uv[:, 1]) * tex_h
    t_span = max(ux, uy, 1e-6)
    ratio = t_span / s_span
    if ratio <= 1.0:
        return 0
    import math
    return min(int(math.floor(math.log2(ratio))), _MAX_MIP)

def _raster_triangle(canvas: np.ndarray, zbuf: np.ndarray,
                     screen: np.ndarray, cam_z: np.ndarray,
                     uv: np.ndarray, tex_rgba: np.ndarray,
                     vert_color: np.ndarray, mip_level: int,
                     alpha_test: bool, blend: bool, debug: bool = False) -> None:
    H, W = canvas.shape[:2]
    x0, y0 = screen.min(axis=0)
    x1, y1 = screen.max(axis=0)
    xmin, xmax = max(0, int(np.floor(x0))), min(W - 1, int(np.ceil(x1)))
    ymin, ymax = max(0, int(np.floor(y0))), min(H - 1, int(np.ceil(y1)))
    if xmin > xmax or ymin > ymax:
        return

    gw = xmax - xmin + 1
    gh = ymax - ymin + 1
    xs = np.arange(xmin, xmax + 1)
    ys = np.arange(ymin, ymax + 1)
    gx, gy = np.meshgrid(xs, ys)
    px = gx + 0.5
    py = gy + 0.5

    (ax, ay), (bx, by), (cx, cy) = screen
    area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
    if abs(area) < 1e-9:
        return
    inv = 1.0 / area
    w0 = ((bx - px) * (cy - by) - (by - py) * (cx - bx)) * inv
    w1 = ((cx - px) * (ay - cy) - (cy - py) * (ax - cx)) * inv
    w2 = 1.0 - w0 - w1

    inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
    if not inside.any():
        return

    depth = w0 * cam_z[0] + w1 * cam_z[1] + w2 * cam_z[2]
    sub_zbuf = zbuf[ymin:ymax + 1, xmin:xmax + 1]
    if blend:
        mask = inside & (depth >= sub_zbuf)
    else:
        mask = inside & (depth > sub_zbuf)
    if not mask.any():
        if blend and debug:
            print(f"  REJECTED blend tri: inside={inside.sum()} depth_range=[{depth.min():.3f},{depth.max():.3f}] zbuf_range=[{sub_zbuf.min():.3f},{sub_zbuf.max():.3f}]", file=sys.stderr)
        return

    covered = np.where(mask.ravel())[0]

    iu = (w0.ravel()[covered] * uv[0, 0] + w1.ravel()[covered] * uv[1, 0] + w2.ravel()[covered] * uv[2, 0])
    iv = (w0.ravel()[covered] * uv[0, 1] + w1.ravel()[covered] * uv[1, 1] + w2.ravel()[covered] * uv[2, 1])
    depth_v = depth.ravel()[covered]

    samples = _sample_texture(tex_rgba, np.column_stack([iu, iv]), mip_level)
    tex_rgb = samples[:, :3].astype(np.float64)
    tex_a = samples[:, 3].astype(np.float64)

    w0c = w0.ravel()[covered]
    w1c = w1.ravel()[covered]
    w2c = w2.ravel()[covered]
    col_all = (w0c[:, None] * vert_color[0]
               + w1c[:, None] * vert_color[1]
               + w2c[:, None] * vert_color[2])
    col_rgb = col_all[:, :3]
    vert_a = col_all[:, 3]

    frag_a = tex_a * (vert_a / 255.0)

    if alpha_test:
        keep = frag_a >= 128.0
        covered = covered[keep]
        tex_rgb = tex_rgb[keep]
        frag_a = frag_a[keep]
        col_rgb = col_rgb[keep]
        depth_v = depth_v[keep]

    frag_rgb = np.clip(tex_rgb * (col_rgb / 255.0), 0, 255)

    ys_w = (covered // gw) + ymin
    xs_w = (covered % gw) + xmin

    if blend and debug:
        print(f"  DRAW blend tri: covered={len(covered)} tex_rgb_mean={tex_rgb.mean(axis=0)} tex_a_mean={tex_a.mean():.1f} frag_rgb_mean={frag_rgb.mean(axis=0)} dst_mean={canvas[ys_w, xs_w, :3].mean(axis=0)}", file=sys.stderr)

    if blend:
        dst = canvas[ys_w, xs_w].astype(np.float64)
        sa = (frag_a / 255.0)[:, None]
        out_rgb = frag_rgb * sa + dst[:, :3] * (1 - sa)
        canvas[ys_w, xs_w, 0:3] = np.clip(out_rgb, 0, 255).astype(np.uint8)
        canvas[ys_w, xs_w, 3] = np.maximum(canvas[ys_w, xs_w, 3], frag_a).astype(np.uint8)
    else:
        canvas[ys_w, xs_w, 0:3] = frag_rgb.astype(np.uint8)
        canvas[ys_w, xs_w, 3] = 255
        zbuf[ys_w, xs_w] = depth_v


def render_block_state(geoms: list[Geometry], pack: ResourcePack,
                       canvas_size: int = 256, yaw: float = 45.0,
                       pitch: float = 30.0, bg: tuple = (20, 20, 24, 255)) -> Image.Image:
    """
    Renders a block-state entry's geometries into a single textured image.

    Pipeline: collect every triangle, rotate into camera space, orthographically
    project, fit to the canvas, then rasterize back-to-front with a z-buffer so
    intersecting faces (e.g. the X-shaped grass plant) resolve correctly.
    """
    # Flatten all triangles across all geometries of this entry. Each triangle
    # remembers which texture + render-type rules apply.
    for g in geoms:
        verts = g.vertices
        if len(verts) < 3:
            continue
        if len(verts) % 3 != 0:
            print(f"  warn: geometry for render type {g.render_type!r} has "
                f"{len(verts)} vertices, not a multiple of 3 — skipping", file=sys.stderr)
            continue

        # Texture source.
        if g.texture is not None:
            img = pack.get(g.texture)
            resolved = img is not None
            if img is None:
                print(f"  warn: texture {g.texture!r} not found in any resource pack — using checker", file=sys.stderr)
                img = _checker(16, 16)
        else:
            resolved = False
            img = _checker(16, 16)

        w0v = verts[0]
        # print(f"  geom render_type={g.render_type!r} texture={g.texture!r} resolved={resolved} "
        #     f"img_size={img.size} first_vert_rgba=({w0v.r},{w0v.g},{w0v.b},{w0v.a}) "
        #     f"first_vert_uv=({w0v.u:.3f},{w0v.v:.3f})", file=sys.stderr)

        tex_rgba = np.asarray(img, dtype=np.float64)
    R = _rot_matrix(yaw, pitch)
    center = np.array([0.5, 0.5, 0.5])

    tris = []  # each: (centroid_z, cam_z[3,3]... see append below)
    for g in geoms:
        verts = g.vertices
        if len(verts) < 3:
            continue
        if len(verts) % 3 != 0:
            print(f"  warn: geometry for render type {g.render_type!r} has "
                  f"{len(verts)} vertices, not a multiple of 3 — skipping", file=sys.stderr)
            continue

        # Texture source.
        if g.texture is not None:
            img = pack.get(g.texture)
            if img is None:
                print(f"  warn: texture {g.texture!r} not found in any resource pack — using checker", file=sys.stderr)
                img = _checker(16, 16)
        else:
            img = _checker(16, 16)
        tex_rgba = np.asarray(img, dtype=np.float64)

        rt = (g.render_type or "").lower()
        KNOWN_OPAQUE = {"solid", "cutout_mipped_all", "cutout", "cutout_mipped"}
        blend = "translucent" in rt or "tripwire" in rt or (":" in rt and rt not in KNOWN_OPAQUE)
        # alpha_test = "cutout" in rt
        # Discard fully-transparent texels on every opaque (non-blend) pass: the
        # texture's own alpha channel is ground truth for what to mask, regardless
        # of the render-type name. A no-op for genuinely solid textures.
        alpha_test = not blend
        # blend = True
        mippable = "mipped" in rt or blend
        # print(f"  render_type={g.render_type!r} -> alpha_test={alpha_test} blend={blend}", file=sys.stderr)

        if "fluid" in rt:
            for w in g.vertices[:3]:
                print(f"    vert rgba=({w.r},{w.g},{w.b},{w.a}) uv=({w.u:.3f},{w.v:.3f})", file=sys.stderr)
            print(f"    texture={g.texture!r}", file=sys.stderr)

        # Format v2: vertices are already a flat, independent triangle list — every
        # 3 vertices is one triangle, no quad/fan reconstruction needed here at all.
        for tstart in range(0, len(verts), 3):
            tverts = verts[tstart:tstart + 3]
            uv_t = _raw_uv(tverts)
            pos_t = np.array([[w.x, w.y, w.z] for w in tverts], dtype=np.float64) - center
            cam_t = pos_t @ R.T
            vertcol_t = np.array([[w.r, w.g, w.b, w.a] for w in tverts], dtype=np.float64)
            tris.append((float(cam_t[:, 2].mean()), cam_t, uv_t, tex_rgba, vertcol_t,
                        alpha_test, blend, mippable))  # <-- extra debug tag

    if not tris:
        return Image.new("RGBA", (canvas_size, canvas_size), bg)

    # for i, t in enumerate(tris):
    #     print(f"  tri {i}: blend={t[6]} tex_shape={t[3].shape} tex_mean={t[3].mean():.1f} "
    #           f"tex_min={t[3].min():.1f} tex_max={t[3].max():.1f}", file=sys.stderr)

    # Painter's order: far first. Camera looks down -Z, so larger cam_z is
    # closer -> ascending sort puts the smallest (farthest) triangles first.
    # (The z-buffer handles per-pixel correctness; sorting just stabilizes
    # alpha blending order for translucent faces.)
    tris.sort(key=lambda t: t[0])

    # Project to screen: fit the rotated model's XY extent into the canvas.
    all_xy = np.vstack([t[1][:, :2] for t in tris])
    mnx, mny = all_xy.min(axis=0)
    mxx, mxy = all_xy.max(axis=0)
    span = max(mxx - mnx, mxy - mny, 1e-6)
    pad = 0.08
    usable = canvas_size * (1 - 2 * pad)
    scale = usable / span
    ox = (canvas_size - (mxx - mnx) * scale) / 2 - mnx * scale
    oy = (canvas_size - (mxy - mny) * scale) / 2 - mny * scale

    canvas = np.zeros((canvas_size, canvas_size, 4), dtype=np.uint8)
    canvas[..., 0] = bg[0]; canvas[..., 1] = bg[1]
    canvas[..., 2] = bg[2]; canvas[..., 3] = bg[3]
    zbuf = np.full((canvas_size, canvas_size), -np.inf, dtype=np.float64)

    # Two-pass draw: opaque geometry first, fully resolved via the z-buffer (order
    # doesn't matter here -- occlusion is correct regardless of draw order). THEN
    # translucent geometry, back-to-front, on top. This must be two passes, not one
    # shared depth-sorted list: the blend path never writes zbuf (by design -- multiple
    # translucent layers need to composite via painter's order, not z-test each other
    # out), so if an opaque triangle is drawn *after* a translucent one in a single
    # merged list, its occlusion test still passes (zbuf wasn't updated by the blend
    # write) and it overwrites the correctly-blended pixels outright. That's exactly
    # what was happening here: the fluid quad computed and wrote the right orange
    # color, and the basin's opaque interior-floor quad (sorted after it by near-equal
    # centroid depth) then painted solid black straight over it.
    opaque_pass = [t for t in tris if not t[6]]
    blend_pass = [t for t in tris if t[6]]

    # for _cz, cam_tri, uv_tri, tex_rgba, vcol, alpha_test, blend, mippable in opaque_pass:
    #     screen = cam_tri[:, :2].copy()
    #     screen[:, 0] = screen[:, 0] * scale + ox
    #     screen[:, 1] = -screen[:, 1] * scale + oy
    #     cam_z = cam_tri[:, 2]
    #     tex_h, tex_w = tex_rgba.shape[:2]
    #     mip = _mip_for_triangle(screen, uv_tri, tex_w, tex_h) if mippable else 0
    #     _raster_triangle(canvas, zbuf, screen, cam_z, uv_tri, tex_rgba,
    #                      vcol, mip, alpha_test, blend)

    for _cz, cam_tri, uv_tri, tex_rgba, vcol, alpha_test, blend, mippable in opaque_pass:
                screen = cam_tri[:, :2].copy()
                screen[:, 0] = screen[:, 0] * scale + ox
                screen[:, 1] = -screen[:, 1] * scale + oy
                cam_z = cam_tri[:, 2]
                tex_h, tex_w = tex_rgba.shape[:2]
                mip = 0
                _raster_triangle(canvas, zbuf, screen, cam_z, uv_tri, tex_rgba,
                                 vcol, mip, alpha_test, blend, debug=False)
    for _cz, cam_tri, uv_tri, tex_rgba, vcol, alpha_test, blend, mippable in blend_pass:
            screen = cam_tri[:, :2].copy()
            screen[:, 0] = screen[:, 0] * scale + ox
            screen[:, 1] = -screen[:, 1] * scale + oy
            cam_z = cam_tri[:, 2]
            tex_h, tex_w = tex_rgba.shape[:2]
            mip = 0
            _raster_triangle(canvas, zbuf, screen, cam_z, uv_tri, tex_rgba,
                             vcol, mip, alpha_test, blend, debug=False)

    return Image.fromarray(canvas, "RGBA")


# --------------------------------------------------------------------------- #
# Contact sheet
# --------------------------------------------------------------------------- #
def build_contact_sheet(images: list[tuple[str, Image.Image]],
                        cols: int = 8, cell: int = 128,
                        label_h: int = 16) -> Image.Image:
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + label_h)), (12, 12, 14, 255))
    from PIL import ImageDraw
    draw = ImageDraw.Draw(sheet)
    for i, (key, img) in enumerate(images):
        r, c = divmod(i, cols)
        thumb = img.resize((cell, cell), Image.NEAREST)
        x = c * cell
        y = r * (cell + label_h)
        sheet.paste(thumb, (x, y))
        label = key.replace("minecraft:", "").replace("[", "\n[") if "[" in key else key
        draw.text((x + 2, y + cell), label.split("\n")[0], fill=(220, 220, 220, 255))
    return sheet


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #
def _safe_name(key: str) -> str:
    return (key.replace(":", "_").replace("[", "_").replace("]", "")
            .replace(",", "_").replace("=", "_").replace("/", "_"))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Render a .vdexgeom file's baked block models to PNGs.")
    ap.add_argument("vdexgeom", help="path to a .vdexgeom file")
    ap.add_argument("--pack", action="append", default=[], metavar="DIR",
                    help="resource-pack root containing assets/<ns>/textures/... (repeatable)")
    ap.add_argument("--outdir", default="rendered", help="output directory (default: ./rendered)")
    ap.add_argument("--size", type=int, default=256, help="per-entry image size in px (default: 256)")
    ap.add_argument("--yaw", type=float, default=45.0, help="camera yaw in degrees (default: 45)")
    ap.add_argument("--pitch", type=float, default=30.0, help="camera pitch in degrees (default: 30)")
    ap.add_argument("--contact-sheet", action="store_true",
                    help="also write a single contact_sheet.png of all entries")
    ap.add_argument("--only", default=None,
                    help="render only entries whose key matches this substring")
    args = ap.parse_args(argv)

    if not args.pack:
        print("error: supply at least one --pack <dir> (a resource-pack root with assets/...)",
              file=sys.stderr)
        return 2

    pack = ResourcePack(args.pack)
    geom = read_vdexgeom(args.vdexgeom)
    print(f"loaded {args.vdexgeom}: {len(geom.texture_palette)} textures, "
          f"{len(geom.render_type_palette)} render types, {len(geom.entries)} block-state entries, "
          f"{len(geom.be_instances)} block-entity instances")

    os.makedirs(args.outdir, exist_ok=True)
    rendered: list[tuple[str, Image.Image]] = []
    for key, geoms in geom.entries:
        if args.only is not None and args.only not in key:
            continue
        img = render_block_state(geoms, pack, canvas_size=args.size,
                                 yaw=args.yaw, pitch=args.pitch)
        name = _safe_name(key) + ".png"
        img.save(os.path.join(args.outdir, name))
        rendered.append((key, img))
        missing = sum(1 for g in geoms if g.texture and pack.get(g.texture) is None)
        flag = f"  ({missing} missing tex)" if missing else ""
        print(f"  rendered {name}  ({len(geoms)} geoms){flag}")

    if args.contact_sheet and rendered:
        sheet = build_contact_sheet(rendered)
        out = os.path.join(args.outdir, "contact_sheet.png")
        sheet.save(out)
        print(f"wrote contact sheet -> {out}")

    print(f"done: {len(rendered)} image(s) in {args.outdir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())