package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChainConveyorBlockEntity.class)
public class MixinChainConveyorBlockEntity extends BlockEntity {
    // This is related to frogports. Technically it's not frogports pulling the packages from chain conveyors, but
    // rather the conveyors visiting paired frogports and exporting packages to them. Putting packages on the conveyor
    // is handled by frogports, though.
    @Inject(method = "exportToPort", at = @At("HEAD"), cancellable = true)
    private void cancelExportIfTooFar(ChainConveyorPackage box, BlockPos offset, CallbackInfoReturnable<Boolean> cir) {
        BlockPos targetPos = getBlockPos().offset(offset);
        if (
            VSGameUtilsKt.getShipManagingPos(level, worldPosition) == VSGameUtilsKt.getShipManagingPos(level, targetPos)
        ) return;
        double dist = CompatUtil.INSTANCE.toSameSpaceAs(
            level,
            getBlockPos().getCenter(),
            targetPos.getCenter()
        ).distanceTo(targetPos.getCenter());
        if (dist > (double)((Integer) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "notifyPortToAnticipate", at = @At("HEAD"), cancellable = true)
    private void cancelNotifyIfTooFar(BlockPos offset, CallbackInfo ci) {
        BlockPos targetPos = getBlockPos().offset(offset);
        if (
            VSGameUtilsKt.getShipManagingPos(level, worldPosition) == VSGameUtilsKt.getShipManagingPos(level, targetPos)
        ) return;
        double dist = CompatUtil.INSTANCE.toSameSpaceAs(
            level,
            getBlockPos().getCenter(),
            targetPos.getCenter()
        ).distanceTo(targetPos.getCenter());
        if (dist > (double)((Integer) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
            ci.cancel();
        }
    }

    // Dummy
    public MixinChainConveyorBlockEntity(BlockEntityType<?> blockEntityType,
        BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }
}
