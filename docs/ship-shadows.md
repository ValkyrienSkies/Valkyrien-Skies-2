# Ship shadows

The client setting `shipShadows` controls visual ship shadows on terrain and other ships. Its default is `false`. Existing config files keep their saved value.

To enable the shadow map, set this value at the root of `config/valkyrienskies/valkyrienskies-client.toml`:

```toml
shipShadows = true
```

Set it to `false` to skip geometry collection, depth rendering, and shadow sampling. A shadow strength of zero also skips the depth pass.

## Shadow maps

The renderer uses three nested, downward depth maps. Each map has 1024 by 1024 texels. The depth textures use 12 MiB in total. The maps provide more detail near the camera. The outer map covers the terrain render distance, with a border for filtering and fading.

Ships use the same cached block shapes as before. These shapes form the depth-pass geometry. Each box uses 32 bytes in ship coordinates. A separate table uses 48 bytes per ship for its transform. Ship motion updates this table. It does not transform and upload every box on the CPU. The renderer draws all boxes with up to three instanced draw calls. It does not search the box list for each visible pixel. The old spatial tree is no longer used by the renderer.

Terrain, batched ships, legacy ships, and Sodium/Embeddium ships read the same depth maps. An upper ship can cast a shadow on a lower ship. Off-screen ships can still cast shadows into the map. Camera motion updates only the maps whose coverage changes. Ship motion, block changes, and deletion update the depth data. Unchanged inputs reuse the maps. Block and chunk changes clear the geometry cache through their own hooks. A batched mesh update from changed light data does not clear this cache.

The maps retain the downward shadow direction. They do not follow the sun. Minecraft face shading, block light, and fullbright surfaces keep their separate calculations. The server setting `Ship interactions.shipShadows` still controls mob light checks separately.

## Settings and appearance

- `shipShadowStrength` sets the amount of sky light removed by a shadow.
- `shipShadowSoftness` sets the filter radius in blocks. Zero uses a hard shadow with texel-edge filtering.
- `shipShadowBlurGrowth` is retained so old config files remain valid. It has no effect on shadow maps.

The filter uses 16 fixed disk samples. Each sample compares four depths before interpolation. Horizontal surfaces use hardware depth comparisons and interpolation. Tilted surfaces use manual comparisons to retain the receiving-plane correction. Both paths read the same depth textures. Map borders blend between adjacent maps. This bounds the work per pixel. The depth-pass cost still grows with ship geometry.

Shadow edges differ from the old box-based sky-visibility estimate. The new filter has a fixed width. It does not widen with ship height. Map resolution limits small and distant shadow detail. These are directional shadows, so self-shadowing at steep face angles can also differ from the old sky-visibility estimate.

## Validation

See `performance-batched-shadows-2026-10-29.md` for the current measurements and checks. See `performance-shadow-map-2026-10-13.md` for the first map implementation. In-game checks remain required for the affected save and active shader packs.

Validation on 13 October 2026: all 215 common tests, 29 shader checks, and the production render checks pass. Both Forge and Fabric JARs build. The files are in `../.spark-analysis/oct13-shadow-map/artifacts/`, with `shadow-map` in their names.
