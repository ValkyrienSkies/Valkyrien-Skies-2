package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SawBlockEntity.class)
public abstract class MixinSawBlockEntity extends BlockBreakingKineticBlockEntity {
    public MixinSawBlockEntity(BlockEntityType<?> type, BlockPos pos,
        BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(
        method = "dropItemFromCutTree",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;subtract(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos shipWorldPosSubtract(BlockPos breakingPos, Vec3i worldPosition, Operation<BlockPos> subtract){
        final Ship shipTree = VSGameUtilsKt.getShipManagingPos(level, breakingPos);
        final Ship shipSaw = VSGameUtilsKt.getShipManagingPos(level, new BlockPos(worldPosition));
        final Vector3d sawWorldPos = VectorConversionsMCKt.toJOML(Vec3.atCenterOf(worldPosition));
        if (shipSaw == null && shipTree == null) return subtract.call(breakingPos, worldPosition);
        if (shipSaw != null) {
            shipSaw.getShipToWorld().transformPosition(sawWorldPos);
        }
        if (shipTree != null) {
            shipTree.getWorldToShip().transformPosition(sawWorldPos);
        }
        return subtract.call(breakingPos, BlockPos.containing(sawWorldPos.x, sawWorldPos.y, sawWorldPos.z));
    }
}
