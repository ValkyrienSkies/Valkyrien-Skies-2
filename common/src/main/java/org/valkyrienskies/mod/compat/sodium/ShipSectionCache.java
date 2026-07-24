package org.valkyrienskies.mod.compat.sodium;

import java.util.List;

public final class ShipSectionCache {
    public final List<ShipSectionCandidate> sections;
    public final int activeChunkCount;
    public final int dirtyGeneration;

    public ShipSectionCache(final List<ShipSectionCandidate> sections, final int activeChunkCount,
        final int dirtyGeneration) {
        this.sections = sections;
        this.activeChunkCount = activeChunkCount;
        this.dirtyGeneration = dirtyGeneration;
    }
}
