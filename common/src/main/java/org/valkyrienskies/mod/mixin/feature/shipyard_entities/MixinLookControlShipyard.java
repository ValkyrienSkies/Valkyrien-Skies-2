package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// LookControl.setLookAt: project the wanted XYZ so it ends up in the same frame as the mob's own position; vanilla's per-tick (wanted - mobPos) delta only makes sense when both live in the same frame. Wraps the deepest setLookAt(DDDFF) overload that all higher-level overloads delegate into.
@Mixin(LookControl.class)
public abstract class MixinLookControlShipyard {

    @Shadow
    @Final
    protected Mob mob;

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapMethod(method = "setLookAt(DDDFF)V")
    private void vs$projectWantedAcrossFrames(
        final double x, final double y, final double z, final float maxYaw, final float maxPitch,
        final Operation<Void> original
    ) {
        final Mob mob = this.mob;
        final Ship mobShip = VSGameUtilsKt.getShipManagingPos(mob.level(), mob.getX(), mob.getY(), mob.getZ());
        final Ship wantedShip = VSGameUtilsKt.getShipManagingPos(mob.level(), x, y, z);

        // Both in the same frame (both world, or both in the same ship's claim) — direct passthrough.
        if (mobShip == wantedShip || (mobShip != null && wantedShip != null && mobShip.getId() == wantedShip.getId())) {
            original.call(x, y, z, maxYaw, maxPitch);
            return;
        }

        // Shipyard mob, world wanted: world → mobShip-local.
        if (mobShip != null && wantedShip == null) {
            final Vector3d shipLocal = mobShip.getTransform().getWorldToShip().transformPosition(
                VS$IN.get().set(x, y, z), VS$OUT.get()
            );
            original.call(shipLocal.x, shipLocal.y, shipLocal.z, maxYaw, maxPitch);
            return;
        }

        // World mob, shipyard wanted: project shipyard → world via wantedShip's shipToWorld.
        if (mobShip == null && wantedShip != null) {
            final Vector3d worldPos = wantedShip.getTransform().getShipToWorld().transformPosition(
                VS$IN.get().set(x, y, z), VS$OUT.get()
            );
            original.call(worldPos.x, worldPos.y, worldPos.z, maxYaw, maxPitch);
            return;
        }

        // Different ships' frames: wanted → world → mobShip-local. Reuse VS$OUT as the intermediate.
        final Vector3d wantedWorld = wantedShip.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(x, y, z), VS$OUT.get()
        );
        final Vector3d wantedInMobShip = mobShip.getTransform().getWorldToShip().transformPosition(wantedWorld);
        original.call(wantedInMobShip.x, wantedInMobShip.y, wantedInMobShip.z, maxYaw, maxPitch);
    }
}
