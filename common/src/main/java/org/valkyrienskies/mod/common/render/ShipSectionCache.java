package org.valkyrienskies.mod.common.render;

import java.util.List;

public final class ShipSectionCache {
    public final List<ShipSectionCandidate> sections;
    public final int activeChunkCount;
    public final long activeChunkSignature;
    public final int dirtyGeneration;

    public ShipSectionCache(final List<ShipSectionCandidate> sections, final int activeChunkCount,
        final long activeChunkSignature, final int dirtyGeneration) {
        this.sections = sections;
        this.activeChunkCount = activeChunkCount;
        this.activeChunkSignature = activeChunkSignature;
        this.dirtyGeneration = dirtyGeneration;
    }
}
