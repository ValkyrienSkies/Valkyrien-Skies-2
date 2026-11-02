package org.valkyrienskies.mod.mixin.feature.ai.goal.parrot;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Notify parrots near a ship-mounted jukebox: vanilla searches an AABB around the (shipyard) jukebox BlockPos which finds zero world-frame parrots; also search at the ship-projected world position.
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererParrotDance {

    @Inject(method = "notifyNearbyEntities", at = @At("TAIL"))
    private void vs$notifyShipParrots(final Level level, final BlockPos jukeboxPos,
        final boolean isPlaying, final CallbackInfo ci) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, jukeboxPos);
        if (ship == null) return;

        final Vector3d worldVec = ship.getShipToWorld().transformPosition(
            new Vector3d(jukeboxPos.getX() + 0.5, jukeboxPos.getY() + 0.5, jukeboxPos.getZ() + 0.5),
            new Vector3d()
        );
        final BlockPos worldPos = BlockPos.containing(worldVec.x, worldVec.y, worldVec.z);
        final AABB worldAabb = new AABB(worldPos).inflate(3.0);
        for (final LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, worldAabb)) {
            entity.setRecordPlayingNearby(jukeboxPos, isPlaying);
        }
    }
}
