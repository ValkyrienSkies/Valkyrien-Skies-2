# Ship interactions

These features are enabled by default. Server settings are in `ShipInteractions` in the mod server configuration.

| Feature | Operation |
| --- | --- |
| Shovel rowing | Stand on a ship. Aim a shovel at water within 4.5 blocks. Use the shovel to move the ship in the horizontal look direction. |
| Fragile blocks | An impact can break blocks in the `valkyrienskies:fragile_ship_collision` block tag. This applies to world blocks and ship blocks. |
| Pressure plates | Ship block contact operates the plate. The plate stays powered during contact. It releases after contact ends. Weighted plates use signal strength 15. |
| Buttons | A ship must push toward the button's mounting surface. The button uses its normal release timer and sound. |
| Levers | A push along the handle changes its state. For a wall lever, a downward push turns it on. An upward push turns it off. |
| Floor and ceiling levers | A push opposite to the block's horizontal facing turns the lever on. A push in the facing direction turns it off. |
| Target blocks | A fast impact gives a redstone pulse. Higher speed gives higher signal strength, up to 15. The pulse lasts eight ticks. |
| Player damage | A ship impact above the closing speed limit causes damage. Damage uses relative speed at the contact point, including ship rotation. |
| Sky shadows | Opaque ship blocks block sky light in mob spawn checks. The built-in world and ship shaders also show vertical sky shadows. |

One shovel stroke consumes one durability point. The stroke applies force at the water contact point. A stroke at one side of the ship can turn it.

All redstone contact checks use ship block shapes. Empty hull space does not operate a device. A ship does not operate its own devices.

Players on the ship do not take impact damage from that ship. Creative players and spectators do not take this damage.

| Server setting | Default | Unit |
| --- | ---: | --- |
| `rowingImpulse` | 4000 | N s per stroke |
| `rowingCooldownTicks` | 10 | Game ticks |
| `fragileBlockForce` | 15000 | N per contact block, estimated |
| `fragileBlockMinImpactSpeed` | 4 | m/s toward the contact surface |
| `impactDuration` | 0.05 | Seconds |
| `leverPushSpeed` | 0.5 | m/s along the handle |
| `targetImpactSpeed` | 4 | m/s toward the target |
| `playerImpactSpeed` | 6 | m/s toward the player |
| `playerImpactDamageScale` | 2 | Damage points per m/s above the limit |
| `maxPlayerImpactDamage` | 40 | Damage points |
| `playerImpactCooldownTicks` | 10 | Game ticks |

Two damage points equal one heart. Each interaction feature has an enable setting. BATCHED always enables visual ship shadows and light from ships. VANILLA and FLYWHEEL use the client setting `dynamicShipToWorldLighting`.

The client settings below control visual darkness. They do not change the server light checks for mob spawning.

| Setting | Default | Range |
| --- | --- | --- |
| `shipShadowStrength` | 0.4 | 0 keeps all sky light; 1 removes all sky light in a ship shadow |
| `shipShadowSoftness` | 0.35 blocks | Base edge fade width, from 0 to 4 blocks |
| `shipShadowBlurGrowth` | 0.08 | Added fade width per block from the ship surface, from 0 to 0.5 |
| `shipShadeStrength` | 0.6 | 0 gives uniform face shade; 1 uses full Minecraft face shade |

Visual shadows blend the lit and shadowed lightmap colors. The default keeps 60 percent of the sky light contribution. Block light remains in the shadow. This avoids the sharp brightness loss from setting the sky light coordinate to zero. The default face shade gives downward faces 70 percent of the upward face brightness before light and ambient occlusion are applied.

Shadow edges use a continuous fade outside the projected box outline. The fade width starts at 0.35 blocks. It grows by 0.08 blocks per block of distance from the nearest ship box surface. The width limit is 8 blocks. This distance is between the ship and the receiving surface. The filter also covers at least one screen pixel to reduce sharp edges at long view distances. Set both width settings to 0 for hard shadows.

Near a ship box, the shadow uses the sky fraction above the nearest box surface. Each outer face limits this fraction. A box side cannot cast a contact shadow above its deck plane. This prevents dark lines at box edges on a flat deck. A face that turns from upward to downward receives a gradual light change. The height fade also prevents a sharp ray cutoff at a tilted box edge. Face shade uses squared normal weights to keep its change smooth during rotation. The full inner coverage away from contact prevents bright seams between adjacent boxes. Overlapping boxes use the greatest coverage, so their shadows do not add extra darkness. This is a visual filter. It does not simulate an area light source or change the server shadow ray.

Ship emitters also supply the render light for mobs, players, and dropped items. The entity light sample uses the current ship light list and the interpolated entity light probe. It keeps sky light, world block light, and fullbright entities. Built-in terrain uses distance from the emitter; Embeddium terrain uses distance along the rotated ship axes. Entity samples use the same falloff as the active terrain renderer. Entity light is sampled once per model, rather than per pixel. The first 128 emitters affect terrain and entity light.

The collision API supplies contact speed, but it does not supply solver force. The break limit uses this estimate:

`force per contact block = reduced mass * closing speed / (impactDuration * contact block count)`

For two moving ships, `reduced mass = 1 / (1 / massA + 1 / massB)`. For a ship against fixed terrain, the estimate uses the ship mass. It excludes rotational inertia. The estimate is not a measured contact force.

The count includes connected blocks on the same contact plane. Each block must touch the other body. A ship block outside the fragile tag on this surface can carry the load for the rigid ship. This protects glass that is level with a ship frame during a flat landing. A frame above the glass, or away from the obstacle, gives no support. World blocks have no rigid frame protection. Contact with nearby stone does not prevent world glass from breaking. Strong world blocks on the edge of the fragile contact surface still share its load. The search stops at these strong blocks. This is a contact load estimate. It does not simulate bending or structural stress.

Only the first collision shape at each reported impact point can break. The search goes into that body. It does not search behind a strong block. All contact blocks are selected before any block is removed. Old contacts cannot then reach a deeper layer in the same tick. Duplicate contacts do not add force. Rebound and contact below the minimum closing speed do not break blocks. A hard impact can still break all exposed fragile blocks in a thin hull with no frame support.

Contact surface work is limited to 4096 blocks per surface and 32768 block reads per server tick. An incomplete surface check does not break blocks. Unloaded chunks stop the remaining check for that tick.

The default fragile tag includes ice, packed ice, blue ice, frosted ice, glass, glass panes, stained glass, stained glass panes, tinted glass, glowstone, and sea lanterns. A data pack can change the tag. Block destruction uses normal drops and block updates.

Levers use the normal Minecraft on and off states. The handle does not have a smooth animation.

Contact detection sweeps a block from its previous position to its current position. It uses the current block orientation. Very fast rotation can make this approximation less accurate.

The server shadow test uses a vertical ray through block shapes. Clear glass transmits sky light. Tinted glass blocks it. This test does not model sky light from adjacent columns. Block light and the other Minecraft spawn rules still apply.

The renderer combines adjacent opaque cubes into boxes. It joins boxes across chunk sections and keeps partial block shapes as separate boxes. Shadow boxes follow the rendered ship position, rotation, and scale. Empty sections do not consume the scan budget. Work is limited to 512 boxes per frame and 524288 cells in loaded, nonempty sections per ship. Ships above these limits can have incomplete visual shadows. These limits do not apply to the server shadow test.

The batched renderer calculates face shade from the rendered surface. Face shade and sky light follow ship rotation. The batched and world renderers share one ship light list per frame. Emitters use the current ship transform, including between game ticks. BATCHED ignores the legacy light and shade settings. Its world light and shadow paths stay active when a saved config has `dynamicShipToWorldLighting = false`. The built-in world renderer and Embeddium use this same rule. Game output still needs a check with the active mod set.

With an Iris shader pack active, the pack controls world shadows. Visual shader behavior needs an in-game check on the target graphics system.

Contact work is limited to 2048 world sections per ship and 32768 cells per contact query. Extreme ship sizes, scales, or motion steps can exceed these limits.

Run the common tests and both loader builds with:

```powershell
.\gradlew.bat :common:test :fabric:remapJar :forge:remapJar
```

Use `scripts/verify-ship-shaders.ps1` with `-MinecraftJar`, `-SodiumJar`, and `-Validator` paths to check the shaders. The script expands shader imports, checks 29 fragment variants, and links the built-in shader pairs with `glslangValidator`.

On Windows, run `scripts/check-ship-lighting-render.ps1 -JavaHome <JDK path>` after the shader check. It uses a hidden OpenGL window. It draws all four world layers and all four batched layers with controlled day and night light data. It checks upright and inverted faces, sky shadows, ship emitter light, and the strength settings. It also checks the edge fade, rotated shadows, clear hull openings, and surfaces above a shadow box. It compares blur at two ship heights. It turns a ship face around both horizontal axes in one-degree steps. It checks the face center and a point near its edge, with and without directional shade. This checks GPU output. It does not replace an in-game check of chunk loading and the active mod set.

For an in-game check:

1. Stand on a floating ship. Use each shovel type at water. Check forward motion, turning, durability, and the stroke delay.
2. Hit glass and ice with the ship. Change `fragileBlockForce` and `fragileBlockMinImpactSpeed`. Check world blocks and ship blocks. Land a glass hull gently and at high speed. Repeat with a frame level with the glass. Check that a raised frame, or a frame away from the obstacle, gives no support.
3. Push each type of pressure plate, button, and lever. Check that plates release and that levers respond only to the handle direction.
4. Hit a target block above and below its speed limit. Check pulse strength and release.
5. Hit a survival player at different speeds. Check damage limits. Check that a passenger takes no damage from the same ship.
6. Put a ship roof over dark ground and over a second ship. Check mob spawn light rules. Add a torch and check that block light prevents spawning.
7. Check moving, rotated, and scaled ship shadows. Check a hull opening, a slab roof, clear glass, and tinted glass.
8. Move a ship torch past a mob, a player, and a dropped item at night. Check that each model receives light. Repeat with Embeddium enabled. Check that entities with their own fullbright renderer retain their brightness.
9. Turn a plank ship slowly from upright to upside down. Check that its face light changes gradually. Move it higher above the ground. Check that the shadow edge becomes wider. Repeat over a second ship.

Automated tests check the force estimate, load sharing, frame support, damage limits, contact geometry, rowing impulse, and shadow geometry. A startup check that stops at the EULA prompt does not verify gameplay.
