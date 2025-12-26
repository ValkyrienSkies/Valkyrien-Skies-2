package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(ChainConveyorBlockEntity.class)
public class MixinChainConveyorBlockEntity extends BlockEntity {
    // This is related to frogports. Technically it's not frogports pulling the packages from chain conveyors, but
    // rather the conveyors visiting paired frogports and exporting packages to them. Putting packages on the conveyor
    // is handled by frogports, though.
    @WrapMethod(method = "exportToPort")
    private boolean cancelExportIfTooFar(ChainConveyorPackage box, BlockPos offset, Operation<Boolean> original) {
        BlockPos targetPos = getBlockPos().offset(offset);
        double dist = CompatUtil.INSTANCE.toSameSpaceAs(
            level,
            getBlockPos().getCenter(),
            targetPos.getCenter()
        ).distanceTo(targetPos.getCenter());
        if (dist <= (double)((Integer) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
            return original.call(box, offset);
        }
        return false;
    }

    @WrapMethod(method = "notifyPortToAnticipate")
    private void cancelNotifyIfTooFar(BlockPos offset, Operation<Void> original) {
        BlockPos targetPos = getBlockPos().offset(offset);
        double dist = CompatUtil.INSTANCE.toSameSpaceAs(
            level,
            getBlockPos().getCenter(),
            targetPos.getCenter()
        ).distanceTo(targetPos.getCenter());
        if (dist <= (double)((Integer) AllConfigs.server().logistics.packagePortRange.get() + 2)) {
            original.call(offset);
        }
    }

    // Dummy
    public MixinChainConveyorBlockEntity(BlockEntityType<?> blockEntityType,
        BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }
}
