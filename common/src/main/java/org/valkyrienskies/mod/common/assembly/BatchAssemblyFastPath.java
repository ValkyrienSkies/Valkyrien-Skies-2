package org.valkyrienskies.mod.common.assembly;

/**
 * Per-thread scope flag that gates the server-side shipyard chunk
 * fast-path in {@code MixinChunkHolderFastPath} (forge) /
 * {@code MixinChunkHolderFastPath} (fabric equivalent).
 *
 * The fast-path builds a bare LevelChunk inline and marks the
 * ChunkHolder's pre-FULL futures complete, skipping the normal
 * EMPTY → ... → FULL pipeline including the disk-read step.
 * That's safe only when we *know* the chunks are fresh — no disk
 * data, no pre-existing block state — which is exactly the case
 * inside {@link ShipAssembler#batchAssembleToShips}'s preload phase.
 *
 * Anywhere else (world startup load, chunk reload after unload,
 * etc.) the fast-path would silently discard disk data — ships
 * would reload with no blocks and fall through their own floors.
 * Gating it behind this flag keeps the spawn-path speedup while
 * letting save/reload round-trip normally.
 */
public final class BatchAssemblyFastPath {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private BatchAssemblyFastPath() {}

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void enter() {
        ACTIVE.set(true);
    }

    public static void exit() {
        ACTIVE.set(false);
    }
}
