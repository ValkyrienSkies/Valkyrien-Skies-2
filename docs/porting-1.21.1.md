# 1.21.1 playtest integration checkpoint

Status: initial local merge and source adaptation, **not a working or validated playtest build**.

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

## Validation performed

- Java parser: all 825 production Java files parse without syntax errors.
- Kotlin compiler PSI parser: all 253 production Kotlin files parse without syntax errors.
- Groovy conversion-phase parsing: root/settings/common/fabric/forge Gradle scripts parse.
- Main mixin configuration JSON parses; entries are unique and every referenced source file exists.
- No remaining conflict markers. These are syntax/structural checks, not type checking, mixin application, or runtime tests.

## Build blocker

Gradle 8.11 and the Architectury plugins were downloaded. Compilation was requested for common, Fabric, and NeoForge. Loom 1.9.428 fails while initializing its platform support, before dependency configuration/source compilation:

```
net.fabricmc.loom.util.CurrentPlatform.isUnixDomainSocketsSupported
java.io.UncheckedIOException: java.net.SocketException: Operation not permitted
```

This environment denies Loom's Unix-domain socket probe. No compiled jar, passing unit-test result, or successful game launch has been produced.

## Remaining port work

1. Run the actual compiler in an environment where Loom initializes. Fix type/API errors; syntax parsing does not establish compilation.
2. Finish adapting the new water/batched/debug rendering code to 1.21's vertex, shader, render-level, and buffer APIs. Audit every new injection against the target bytecode, including fluid occlusion and block-entity rendering.
3. Reconcile optional compatibility dependency coordinates, exclusions inherited from the old 1.21 base, and new compatibility sources. Several pre-existing coordinates still point at 1.20.1. No compatibility parity is claimed.
4. Audit item/entity/block API changes in features restored from the destination's placeholders/exclusions; ensure registries initialize identically on both loaders.
5. Run common and platform tests, then launch both clients and dedicated servers. Exercise ship creation, save/reload, tracking, movement, collision, assembly/welding, VDEX, flooding, retained water/droplets, and renderer reload/disconnect.
6. Compare performance and behavior with current 1.20.1 playtest. Keep CI publishing/deployment disabled for this branch until the port is validated.
