# 1.21.1 playtest integration checkpoint

Status: local merge plus rendering/entity API adaptation, **not a working or validated playtest build**.

## Inputs

- Destination: `1.21.1/playtest`, created from `1.21.1/main` at `f39132148e717d325933b4ce6e9e9fb13d929390`.
- Merged source: `1.20.1/playtest` at `249ded474063cb677192e37dd750f55c7b325369`.
- The merge started with 67 conflicted paths. Both branch histories are preserved.
- Nothing has been pushed or published.

## Resolution decisions

- Retain Java 21, Minecraft 1.21.1, Fabric/NeoForge, target loader metadata, component registration, and 1.21 chunk/render signatures from the destination.
- Use current playtest's vs-core/Krunch/API dependency versions together. These libraries are not being ported to another Minecraft version.
- Retain current playtest's movement snapshots, packet batching, terrain-ingestion budgets, unload queues, fluid systems, welding, VDEX, and backend/config commands rather than restoring older destination implementations.
- Combine the destination's NeoForge config loading bridge with playtest's path-aware config commands. Remove the destination's unconditional synchronous-physics/load-freeze overrides.
- Adapt client chunk sending/dropping and chunk results to the target API while retaining playtest batching. Use 1.21 render sections directly instead of the removed RenderChunkInfo wrapper.
- Retain the registered-batch disk-read shortcut, but use the destination's ChunkStatusTasks worldgen-stage hooks instead of the removed ChunkMap.schedule path. Leave vanilla generation-holder/light lifecycle intact.
- Verify and correct renderer method names/descriptors against Mojang's 1.21.1 mappings, including renderSectionLayer, isSectionCompiled, and renderLevel.
- Port tool selection storage to CUSTOM_DATA and block-entity save/load calls to registry-aware APIs.
- Keep source removals/moves made by current playtest; retain the 1.21-specific generation-holder and Immersive Portals hooks. Deduplicate mixin registrations and remove references to source files removed by the merge.

## Follow-up port changes

- Migrate retained-water, droplet, flooded-voxel, batched-ship, and debug rendering to the 1.21 `VertexConsumer` setters and packed vertex API.
- Replace persistent `BufferBuilder` reuse with `ByteBufferBuilder` storage and per-batch builders. Upload `MeshData`, retain its centroid-based `SortState`, and rebuild index buffers when the camera moves. Close native storage on renderer reset.
- Convert sprite UV inputs from the old 0..16 convention to 1.21's normalized coordinates. Preserve the custom no-shade alpha marker in the updated bulk vertex method.
- Update Iris 1.8.7's `beginBlock` signature, including block emission. Pin Iris to the release that requires the existing Sodium 0.6.9 dependency.
- Adapt physics entity synchronization, spawn packets, interpolation, packed model colors, and portal-state copying to the 1.21 entity API.
- Add the NeoForge repository and its explicit compile-only universal API jar for common config types.
- Correct KotlinForForge repository filtering, JEI coordinates, and common compile-only Create Big Cannons/RPL/Vista APIs. The latter use published 1.21.1 NeoForge artifacts with Mojang names. This does not establish cross-loader compatibility.
- Prevent old Create/Moonlight transitive dependency graphs from entering the common target classpath; remove obsolete split Immersive Portals coordinates.

- Compiler-driven follow-up: restore creative-tab imports; use optional selector bounds, tooltip contexts, resource-location factories, nullable bucket-pickup player, spawn-placement APIs, stream-codec vehicle packet decoding, and six-argument interpolation.
- Preserve assembly preload behavior through the public 1.21 chunk-future entry point. Rename the path-type enum and point the raw-state invoker at `getPathTypeFromState`.
- Update the Sodium options UI/storage package names to Sodium 0.6.

## Validation performed

- Java parser: all 825 production Java files parse without syntax errors.
- Kotlin compiler PSI parser: all 253 production Kotlin files parse without syntax errors.
- Groovy conversion-phase parsing: root/settings/common/fabric/forge Gradle scripts parse.
- Main mixin configuration JSON parses; entries are unique and every referenced source file exists.
- No remaining conflict markers. These are syntax/structural checks, not type checking, mixin application, or runtime tests.

- The exterior-fog render-level call, depth-clear call, translucent-layer calls, and section-dirty signature exist in actual 1.21.1 bytecode. This does not validate mixin application.
- Two focused JUnit tests pass against the actual remapped Minecraft 1.21.1 client classes: vertex offset/attribute forwarding and translucent mesh sorting after the original mesh closes. These were run independently of Gradle; no mock Minecraft classes were used.
- The offset consumer, vanilla batched-model adapter/interface, and bulk-vertex shader mixin type-check against those actual target classes. This is limited type checking, not a complete module build.

## Build progress and blockers

Gradle 8.11 and the Architectury plugins were downloaded. Compilation has been requested for common, Fabric, and NeoForge. Loom 1.9.428 initially failed before dependency setup because this environment denies its optional Unix-domain socket probe:

```
net.fabricmc.loom.util.CurrentPlatform.isUnixDomainSocketsSupported
java.io.UncheckedIOException: java.net.SocketException: Operation not permitted
```

A temporary build-only Java instrumentation shim reports that optional capability as unavailable, with Gradle's own instrumentation agent disabled. It lives outside the repository and is not included in the mod. No permissions were changed. This allowed Loom to proceed through Minecraft setup into dependency resolution/remapping.

The next configuration failure identified the inherited 1.20.1 Vista artifact as requiring newer Loom. The compile-only reference now uses Vista 1.21.1-5.6.5's Mojang-mapped NeoForge artifact. Common project configuration and compile-classpath resolution now complete. Gradle runs the Kotlin compiler. The first pass reported 127 Kotlin diagnostics; after the API corrections, the latest pass reports **12 diagnostics in two files**:

- `common/config/ShipRenderer.kt`: the inherited source exclusions omit `FlywheelCompat`, which the current playtest renderer selection now references (2 diagnostics).
- `compat/jei/NumericAttributeStorage.kt`: the selected JEI 1.21.1 API lacks playtest's `mezz.jei.api.search` interfaces (10 diagnostics).

These compatibility features have not been deleted or replaced with stubs to force compilation. The Java compile task is downstream of Kotlin and has not run, so the count is not an estimate of all remaining port errors. Platform configuration was stopped to prioritize common compiler feedback. No complete module compilation, mod jar, or successful game launch has been produced.

## Remaining port work

1. Finish dependency setup and run the complete module compiler. Fix type/API errors; focused tests and syntax parsing do not establish a full build.
2. Validate the adapted water/batched/debug rendering paths in game and audit remaining shader/render-level APIs. Audit every new injection against the target bytecode, including fluid occlusion and block-entity rendering.
3. Reconcile optional compatibility dependency coordinates, exclusions inherited from the old 1.21 base, and new compatibility sources. Several pre-existing coordinates still point at 1.20.1. No compatibility parity is claimed.
4. Audit item/entity/block API changes in features restored from the destination's placeholders/exclusions; ensure registries initialize identically on both loaders.
5. Run common and platform tests, then launch both clients and dedicated servers. Exercise ship creation, save/reload, tracking, movement, collision, assembly/welding, VDEX, flooding, retained water/droplets, and renderer reload/disconnect.
6. Compare performance and behavior with current 1.20.1 playtest. Keep CI publishing/deployment disabled for this branch until the port is validated.
