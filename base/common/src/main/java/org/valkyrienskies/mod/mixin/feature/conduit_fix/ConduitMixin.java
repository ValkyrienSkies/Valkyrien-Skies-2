package org.valkyrienskies.mod.mixin.feature.conduit_fix;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ConduitBlockEntity.class)
public class ConduitMixin extends BlockEntity {

    public ConduitMixin(final BlockEntityType<?> blockEntityType, final BlockPos blockPos,
        final BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Redirect(
        method = "applyEffects",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        ),
        require = 0
    )
    private static boolean closerThan(final BlockPos instance, final Vec3i vec3i, final double distance,
        final Level level, final BlockPos blockPos, final List<BlockPos> list) {
        final double retValue =
            VSGameUtilsKt.squaredDistanceBetweenInclShips(level, instance.getX(), instance.getY(), instance.getZ(),
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
        ),
        require = 0
    )
    private static boolean closerThan2(final BlockPos instance, final Vec3i vec3i, final double distance,
        final Level level,
        final BlockPos blockPos, final BlockState blockState, final List<BlockPos> list,
        final ConduitBlockEntity conduitBlockEntity) {
        return closerThan(instance, vec3i, distance, level, blockPos, list);
    }
}
