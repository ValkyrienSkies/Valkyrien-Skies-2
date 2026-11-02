package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.ShipPathfindingUtils;

@Mixin(MoveControl.class)
public class MixinMoveControl {

    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;blockPosition()Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos vs$useShipSupportForStepChecks(final Mob instance, final Operation<BlockPos> original) {
        final BlockPos blockPos = original.call(instance);
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            return blockPos;
        }

        final BlockState worldState = this.mob.level().getBlockState(blockPos);
        if (!worldState.isAir()) {
            return blockPos;
        }

        final BlockPos supportPos = ShipPathfindingUtils.findSupportingShipBlock(this.mob.level(), this.mob,
            this.mob.getBoundingBox());
        return supportPos != null ? supportPos : blockPos;
    }
}
