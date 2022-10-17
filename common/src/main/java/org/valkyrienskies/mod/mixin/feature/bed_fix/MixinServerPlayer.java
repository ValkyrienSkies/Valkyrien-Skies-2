package org.valkyrienskies.mod.mixin.feature.bed_fix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Entity {

    public MixinServerPlayer(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(
        at = @At("TAIL"),
        method = "isReachableBedBlock",
        cancellable = true
    )
    private void isReachableBedBlock(final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            final Vec3 vec3 = Vec3.atBottomCenterOf(blockPos);

            final double origX = vec3.x;
            final double origY = vec3.y;
            final double origZ = vec3.z;

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, origX, origY, origZ, 1, (x, y, z) -> {
                cir.setReturnValue(Math.abs(this.getX() - x) <= 3.0 && Math.abs(this.getY() - y) <= 2.0 &&
                    Math.abs(this.getZ() - z) <= 3.0);
            });
        }
    }

}
