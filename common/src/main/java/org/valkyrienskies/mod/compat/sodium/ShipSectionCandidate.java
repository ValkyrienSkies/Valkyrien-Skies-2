package org.valkyrienskies.mod.compat.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;

public final class ShipSectionCandidate {
    public final int x;
    public final int y;
    public final int z;
    public final RenderSection section;

    public ShipSectionCandidate(final int x, final int y, final int z, final RenderSection section) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.section = section;
    }
}
