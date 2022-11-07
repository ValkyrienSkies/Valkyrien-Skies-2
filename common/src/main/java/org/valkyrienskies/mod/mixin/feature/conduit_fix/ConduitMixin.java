package org.valkyrienskies.mod.mixin.feature.conduit_fix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ConduitBlockEntity.class)
public class ConduitMixin extends BlockEntity {

    public ConduitMixin(final BlockEntityType<?> blockEntityType) {
        super(blockEntityType);
    }

    @Redirect(
        method = "applyEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        )
    )
    public boolean closerThan(final BlockPos instance, final Vec3i vec3i, final double distance) {
        final double retValue =
            VSGameUtilsKt.squaredDistanceBetweenInclShips(this.level, instance.getX(), instance.getY(), instance.getZ(),
                vec3i.getX(),
                vec3i.getY(),
                vec3i.getZ());
        return retValue < distance * distance;
    }

    @Redirect(
        method = "updateDestroyTarget",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        )
    )
    public boolean closerThan2(final BlockPos instance, final Vec3i vec3i, final double distance) {
        return closerThan(instance, vec3i, distance);
    }
}
