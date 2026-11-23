package org.valkyrienskies.mod.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.util.EntityRenderPosition;

public final class RiderPresentation {
    public final float time;
    public final VsiClientShipWorld world;
    public final Map<Entity, EntityRenderPosition.Snapshot> snapshots = new IdentityHashMap<>();
    public RiderPresentation(float time, VsiClientShipWorld world) { this.time = time; this.world = world; }

    public void restore() {
        snapshots.forEach((entity, snapshot) -> snapshot.restore(entity));
    }
}
