package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Allow spawn if either the ship-frame OR world-frame walkTargetValue passes vanilla's >=0 gate.
@Mixin(PathfinderMob.class)
public abstract class MixinPathfinderMobSpawnRules {

    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void vs$shipAwareCheckSpawnRules(
        final LevelAccessor level, final MobSpawnType spawnType,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        final PathfinderMob self = (PathfinderMob) (Object) this;
        final BlockPos shipPos = self.blockPosition();
        final Ship ship = VSGameUtilsKt.getShipManagingPos(serverLevel, shipPos);
        if (ship == null) return;
        final Vector3d rendered = ship.getTransform().getShipToWorld().transformPosition(
            shipPos.getX() + 0.5, shipPos.getY() + 0.5, shipPos.getZ() + 0.5, new Vector3d()
        );
        final BlockPos worldPos = BlockPos.containing(rendered.x, rendered.y, rendered.z);
        final float shipWalkValue = self.getWalkTargetValue(shipPos, level);
        final float worldWalkValue = self.getWalkTargetValue(worldPos, level);
        cir.setReturnValue(Math.max(shipWalkValue, worldWalkValue) >= 0.0F);
    }
}
