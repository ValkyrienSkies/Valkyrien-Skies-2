package org.valkyrienskies.mod.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.util.EntityRenderPosition;
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck;

public final class RiderPresentation {
    public final float time;
    public final VsiClientShipWorld world;
    public final Map<Entity, EntityRenderPosition.Snapshot> snapshots = new IdentityHashMap<>();
    private LocalPlayerDuck player;
    private MountedCameraRotation previousCamera;

    public RiderPresentation(float time, VsiClientShipWorld world) { this.time = time; this.world = world; }

    public void presentCamera(LocalPlayerDuck player) {
        this.player = player;
        previousCamera = player.vs$exchangeFrameRotation(null);
        player.vs$updateMountedCameraRotation(true);
    }

    public void restore() {
        try { snapshots.forEach((entity, snapshot) -> snapshot.restore(entity)); }
        finally { if (player != null) player.vs$exchangeFrameRotation(previousCamera); }
    }
}
