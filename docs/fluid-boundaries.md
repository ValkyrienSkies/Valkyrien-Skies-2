# Fluid boundaries

FF03 gives flooding its own block-state geometry, independent of collision and friction. It uses the main voxel body's existing block grid, with openings and occupied volume resolved at 1/16 block. Disconnected empty regions inside one block keep separate fluid balances.

With the existing topology, flooding, cellular automaton and inter-body transfer settings enabled, water enters and exits through these openings. Slabs reduce capacity; closed doors and trapdoors block the faces covered by their shape; open states and iron bars expose their actual gaps. Ordinary block-state updates also update the boundary. Waterlogging does not change the solid boundary or replace vanilla water sources.

## Registration and overrides

The default is the block state's outline shape, before collision simplification. Solid subcells round outward to avoid leaks through geometry smaller than 1/16 block. Empty shapes are open. Shapes extending outside their block, non-finite geometry, and shape-query failures fall back to a closed block. Position-dependent modded shapes are queried in the registration context; use an override when that context cannot describe the intended permeability.

Existing `vs_mass` datapack entries accept `fluid_boundary`:

| Value | Behavior |
| --- | --- |
| `shape` or omitted | Use the state shape and conservative fallback above. |
| `open` | Allow flooding through the entire block, regardless of collision. |
| `closed` | Block flooding through the entire block, including with `no_collision`. |

For example, add `"fluid_boundary": "closed"` to an existing block or tag entry. Keep its mass, friction, elasticity and priority values: the normal datapack entry precedence still applies. Invalid override names are rejected.

Older API registrations without independent boundary data keep the existing coarse behavior. Boundary definitions and separate component balances persist through saves, splits and welds.

## Scope

Gameplay point and volume queries and retained-water rendering consume the partial regions. Fine exterior-water shader masks remain FF11 work; partial cells do not use the old whole-block dry mask, which would incorrectly hide water outside their voids. Gravity-oriented rendering remains FF10. FF03 does not change bucket source placement (FF02), add child-body fluid grids (FF04), or implement persistent gas and multi-fluid mixing (FF06/FF07).
