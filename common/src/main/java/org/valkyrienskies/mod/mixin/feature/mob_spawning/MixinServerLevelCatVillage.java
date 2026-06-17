package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// CatSpawner gates village-cat spawning on ServerLevel.isCloseToVillage, whose villageDistanceTracker propagates per-section but doesn't cross between world and shipyard section keys; for a ship-mounted village the world-coord candidate sees no nearby village section and no cat ever spawns. Project the candidate into each nearby ship's local space and query sectionsToVillage there too.
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelCatVillage {

    @Inject(method = "isCloseToVillage", at = @At("HEAD"), cancellable = true)
    private void vs$alsoConsiderShipVillages(
        final BlockPos pos, final int range,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (range > 6) return;
        final ServerLevel self = (ServerLevel) (Object) this;

        // (6 - range + 1) sections of slop, × 16 blocks/section for the world-AABB ship search.
        final double searchRadius = (6 - range + 1) * 16.0;
        final double cx = pos.getX() + 0.5;
        final double cy = pos.getY() + 0.5;
        final double cz = pos.getZ() + 0.5;
        final AABBd searchAabb = new AABBd(
            cx - searchRadius, cy - searchRadius, cz - searchRadius,
            cx + searchRadius, cy + searchRadius, cz + searchRadius
        );

        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(self, searchAabb)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(cx, cy, cz), new Vector3d()
            );
            final BlockPos shipLocalPos = BlockPos.containing(
                shipLocal.x, shipLocal.y, shipLocal.z
            );
            final int sections = self.sectionsToVillage(SectionPos.of(shipLocalPos));
            if (sections <= 6 - range) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
