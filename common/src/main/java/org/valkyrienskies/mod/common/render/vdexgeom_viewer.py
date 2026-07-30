#!/usr/bin/env python3
"""
vdexgeom_viewer.py — interactive mouse-orbit viewer for .vdexgeom files.

Companion to vdexgeom_render.py: same parser and same software rasterizer,
just re-rendered on every drag instead of written once to disk. Nothing in
vdexgeom_render.py is modified — this only imports its public pieces
(read_vdexgeom, ResourcePack, render_block_state, VdexGeom) and wraps them
in a small Tkinter window.

Controls
  left-drag         orbit (yaw / pitch)
  scroll wheel      zoom in / out
  double-click      reset view (yaw=45, pitch=30, zoom=1)
  entry list        click a block-state key to switch models
  <- / -> or [ / ]  step through entries with the keyboard

Usage
  python3 vdexgeom_viewer.py path/to/model.vdexgeom --pack path/to/resourcepack [--pack ...]
  python3 vdexgeom_viewer.py path/to/model.vdexgeom --pack ./assets --only diorite
  python3 vdexgeom_viewer.py path/to/model.vdexgeom --pack ./assets --key "minecraft:stone"

Requires a display (tkinter) in addition to vdexgeom_render.py's own
dependencies (numpy, Pillow). The rasterizer is still pure-CPU/numpy — no GPU
path — so to keep orbiting smooth this uses a two-tier render pipeline:

  * While the mouse is actively moving (dragging, or a burst of scroll-wheel
    zooms), every frame is a cheap, low-resolution, triangle-decimated
    preview rendered synchronously on the UI thread. It's blocky, but it's
    fast enough to track the mouse in real time even on triangle-heavy ships.
  * Once motion settles for a short moment, one full-resolution, full-detail
    render is kicked off on a background thread (so the UI never blocks) and
    swapped in as soon as it's ready. A monotonic generation counter drops
    any full render that's still in flight when the user moves again, so
    stale frames never clobber a newer preview.
"""

from __future__ import annotations

import argparse
import sys
import threading

try:
    import tkinter as tk
except ImportError:
    print("error: tkinter is not available in this Python install. "
          "On Debian/Ubuntu: sudo apt install python3-tk", file=sys.stderr)
    sys.exit(1)

from PIL import Image, ImageTk

from vdexgeom_render import (
    Geometry,
    VdexGeom,
    ResourcePack,
    read_vdexgeom,
    render_block_state,
)

DEFAULT_YAW = 45.0
DEFAULT_PITCH = 30.0
DEFAULT_ZOOM = 1.0

# Two-tier render tuning. Preview frames are cheap (small canvas + decimated
# triangle count) so they can be drawn synchronously on every mouse-move
# without blocking the UI; the full-quality frame only runs once motion has
# settled, off the UI thread, so a slow render never causes a freeze.
_SETTLE_DELAY_MS = 120        # idle time after the last move before a full render starts
_PREVIEW_MAX_SIDE = 220       # internal canvas cap for preview frames (px)
_PREVIEW_MIN_SIDE = 48
_PREVIEW_SCALE = 0.45         # preview internal size = size/zoom * this, clamped
_PREVIEW_MAX_TRIS = 3500      # decimate any entry with more triangles than this for previews


def _decimate_geoms(geoms: list[Geometry], max_tris: int) -> list[Geometry]:
    """
    Returns a cheap stand-in for `geoms` with a bounded triangle count, for
    fast preview frames only. The real render_block_state() is unchanged and
    still consumes the full, undecimated geometry.

    Per-triangle numpy call overhead (not just fill-rate) dominates cost for
    ship-scale entries with tens of thousands of triangles, so shrinking the
    canvas alone isn't enough to keep drags smooth — the triangle *count*
    itself has to drop too. This takes every Nth triangle per geometry
    (stride chosen so the total stays under max_tris), which is a fast,
    allocation-light way to get a recognizable silhouette without touching
    the source vertex data.
    """
    total = sum(len(g.vertices) // 3 for g in geoms)
    if total <= max_tris or total == 0:
        return geoms
    stride = max(1, -(-total // max_tris))  # ceil(total / max_tris)
    out: list[Geometry] = []
    for g in geoms:
        tri_count = len(g.vertices) // 3
        if tri_count <= 1:
            out.append(g)
            continue
        kept = []
        for t in range(0, tri_count, stride):
            kept.extend(g.vertices[t * 3:t * 3 + 3])
        if not kept:
            kept = g.vertices[:3]
        out.append(Geometry(g.render_type, g.texture, g.cull_face, kept))
    return out


class Viewer:
    def __init__(self, geom: VdexGeom, pack: ResourcePack, *,
                 size: int = 512, key_filter: str | None = None,
                 start_key: str | None = None):
        self.pack = pack
        self.size = size
        self.entries = [(k, g) for k, g in geom.entries
                        if key_filter is None or key_filter in k]
        if not self.entries:
            raise SystemExit("no block-state entries matched the given filter")

        self.idx = 0
        if start_key is not None:
            for i, (k, _g) in enumerate(self.entries):
                if start_key in k:
                    self.idx = i
                    break

        self.yaw = DEFAULT_YAW
        self.pitch = DEFAULT_PITCH
        self.zoom = DEFAULT_ZOOM

        self._drag_last: tuple[int, int] | None = None
        self._settle_job: str | None = None       # pending "start full render" timer
        self._render_generation = 0                # invalidates stale background renders
        self._full_render_pending = False
        self._tkimg: ImageTk.PhotoImage | None = None  # keep a live reference
        self._preview_cache: dict[int, list[Geometry]] = {}  # idx -> decimated geoms

        self._build_ui()
        self._render_full_blocking()  # first frame: no reason to wait, just show it

    # -- UI ----------------------------------------------------------------
    def _build_ui(self) -> None:
        self.root = tk.Tk()
        self.root.title("vdexgeom viewer")
        self.root.configure(bg="#141416")

        # left: scrollable list of block-state keys
        left = tk.Frame(self.root, bg="#141416")
        left.pack(side=tk.LEFT, fill=tk.Y)

        list_scroll = tk.Scrollbar(left)
        list_scroll.pack(side=tk.RIGHT, fill=tk.Y)

        self.listbox = tk.Listbox(left, width=42, activestyle="none",
                                  bg="#1c1c20", fg="#e6e6e6",
                                  selectbackground="#3a6ea5",
                                  yscrollcommand=list_scroll.set,
                                  exportselection=False)
        for key, _geoms in self.entries:
            self.listbox.insert(tk.END, key)
        self.listbox.selection_set(self.idx)
        self.listbox.activate(self.idx)
        self.listbox.see(self.idx)
        self.listbox.pack(side=tk.LEFT, fill=tk.Y)
        list_scroll.config(command=self.listbox.yview)
        self.listbox.bind("<<ListboxSelect>>", self._on_select_entry)

        # right: canvas image + status bar
        right = tk.Frame(self.root, bg="#141416")
        right.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        self.image_label = tk.Label(right, bg="#141416", cursor="fleur")
        self.image_label.pack(side=tk.TOP)
        self.image_label.bind("<ButtonPress-1>", self._on_press)
        self.image_label.bind("<B1-Motion>", self._on_drag)
        self.image_label.bind("<ButtonRelease-1>", self._on_release)
        self.image_label.bind("<Double-Button-1>", self._on_reset)
        # scroll wheel: Windows/macOS send <MouseWheel>, X11 sends Button-4/5
        self.image_label.bind("<MouseWheel>", self._on_wheel)
        self.image_label.bind("<Button-4>", lambda e: self._zoom_by(1.1))
        self.image_label.bind("<Button-5>", lambda e: self._zoom_by(1 / 1.1))

        self.status = tk.Label(right, anchor="w", justify=tk.LEFT,
                               bg="#141416", fg="#9a9a9a",
                               font=("TkDefaultFont", 9))
        self.status.pack(side=tk.TOP, fill=tk.X, padx=6, pady=(4, 6))

        self.root.bind("<Left>", lambda e: self._step_entry(-1))
        self.root.bind("<Right>", lambda e: self._step_entry(1))
        self.root.bind("[", lambda e: self._step_entry(-1))
        self.root.bind("]", lambda e: self._step_entry(1))
        self.root.bind("r", lambda e: self._on_reset(None))

    # -- mouse handlers ------------------------------------------------------
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
        # event.delta is +/-120 per notch on Windows, variable on macOS.
        factor = 1.1 if event.delta > 0 else 1 / 1.1
        self._zoom_by(factor)

    def _zoom_by(self, factor: float) -> None:
        self.zoom = max(0.15, min(8.0, self.zoom * factor))
        self._interaction_changed()

    def _on_reset(self, _event) -> None:
        self.yaw, self.pitch, self.zoom = DEFAULT_YAW, DEFAULT_PITCH, DEFAULT_ZOOM
        self._interaction_changed()

    def _on_select_entry(self, _event) -> None:
        sel = self.listbox.curselection()
        if not sel:
            return
        self.idx = sel[0]
        self._interaction_changed()

    def _step_entry(self, delta: int) -> None:
        self.idx = (self.idx + delta) % len(self.entries)
        self.listbox.selection_clear(0, tk.END)
        self.listbox.selection_set(self.idx)
        self.listbox.see(self.idx)
        self._interaction_changed()

    # -- two-tier rendering ----------------------------------------------------
    def _interaction_changed(self) -> None:
        """Called on every yaw/pitch/zoom/entry change: draw a cheap preview
        right now, and (re)start the settle timer for a full-quality render."""
        self._render_preview()
        if self._settle_job is not None:
            self.root.after_cancel(self._settle_job)
        self._settle_job = self.root.after(_SETTLE_DELAY_MS, self._start_full_render)

    def _preview_geoms(self) -> list[Geometry]:
        cached = self._preview_cache.get(self.idx)
        if cached is not None:
            return cached
        _key, geoms = self.entries[self.idx]
        decimated = _decimate_geoms(geoms, _PREVIEW_MAX_TRIS)
        self._preview_cache[self.idx] = decimated
        return decimated

    def _internal_size_for(self, display_size: int) -> int:
        return max(16, int(round(display_size / self.zoom)))

    def _render_preview(self) -> None:
        """Fast, synchronous, low-res + decimated-triangle frame. Cheap enough
        to call on every single mouse-move without the UI stuttering."""
        geoms = self._preview_geoms()
        preview_side = max(_PREVIEW_MIN_SIDE,
                           min(_PREVIEW_MAX_SIDE,
                               int(round(self._internal_size_for(self.size) * _PREVIEW_SCALE))))
        img = render_block_state(geoms, self.pack, canvas_size=preview_side,
                                 yaw=self.yaw, pitch=self.pitch)
        img = img.resize((self.size, self.size), Image.NEAREST)
        self._show_image(img, refining=True)

    def _start_full_render(self) -> None:
        """Kicks off one full-resolution, full-detail render on a background
        thread once the mouse has been still for _SETTLE_DELAY_MS."""
        self._settle_job = None
        self._render_generation += 1
        gen = self._render_generation
        self._full_render_pending = True

        _key, geoms = self.entries[self.idx]
        yaw, pitch, size = self.yaw, self.pitch, self.size
        internal_size = self._internal_size_for(size)

        def work() -> None:
            img = render_block_state(geoms, self.pack, canvas_size=internal_size,
                                     yaw=yaw, pitch=pitch)
            if internal_size != size:
                resample = Image.NEAREST if internal_size < size else Image.BOX
                img = img.resize((size, size), resample)
            # Hand the result back to the UI thread; Tk widgets must only be
            # touched from there. `gen` lets _apply_full_render discard this
            # if the user has already moved on to a newer view.
            self.root.after(0, lambda: self._apply_full_render(gen, img))

        threading.Thread(target=work, daemon=True).start()

    def _apply_full_render(self, gen: int, img: Image.Image) -> None:
        if gen != self._render_generation:
            return  # stale — a newer preview/render has since superseded this one
        self._full_render_pending = False
        self._show_image(img, refining=False)

    def _render_full_blocking(self) -> None:
        """Used only for the very first frame on startup, where there's no
        prior preview to show and a short synchronous wait is unnoticeable."""
        self._render_generation += 1
        self._render_preview()
        self._start_full_render()

    def _show_image(self, img: Image.Image, *, refining: bool) -> None:
        self._tkimg = ImageTk.PhotoImage(img)
        self.image_label.configure(image=self._tkimg)
        self._update_status(refining=refining)

    def _update_status(self, *, refining: bool) -> None:
        key, geoms = self.entries[self.idx]
        n_tris = sum(len(g.vertices) // 3 for g in geoms)
        tag = "  (refining…)" if refining else ""
        self.status.configure(
            text=(f"{key}{tag}\n"
                  f"yaw {self.yaw:6.1f}°   pitch {self.pitch:6.1f}°   "
                  f"zoom {self.zoom:4.2f}x   {n_tris} tris   "
                  f"entry {self.idx + 1}/{len(self.entries)}   "
                  f"(drag=orbit, wheel=zoom, dbl-click/r=reset, ←/→=switch)")
        )

    def run(self) -> None:
        self.root.mainloop()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="Interactively orbit a .vdexgeom file's baked block models with the mouse.")
    ap.add_argument("vdexgeom", help="path to a .vdexgeom file")
    ap.add_argument("--pack", action="append", default=[], metavar="DIR",
                    help="resource-pack root containing assets/<ns>/textures/... (repeatable)")
    ap.add_argument("--size", type=int, default=512, help="viewport size in px (default: 512)")
    ap.add_argument("--only", default=None,
                    help="only list/show entries whose key matches this substring")
    ap.add_argument("--key", default=None,
                    help="open with this entry selected initially (substring match)")
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

    viewer = Viewer(geom, pack, size=args.size, key_filter=args.only, start_key=args.key)
    viewer.run()
    return 0


if __name__ == "__main__":
    sys.exit(main())