package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.MechanicalArmCompat;

@Mixin(ArmBlockEntity.class)
public abstract class MixinArmBlockEntity extends BlockEntity {
    @Shadow ListTag interactionPointTag;

    protected MixinArmBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(
        method = {"searchForItem", "searchForDestination", "simulateInsertion", "collectItem", "depositItem"},
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;isValid()Z"),
        remap = false,
        require = 5
    )
    private boolean vs$checkReachBeforeTransfer(ArmInteractionPoint point, Operation<Boolean> original) {
        // Check again at pickup/deposit: a ship may have moved since the arm selected this point.
        // Let Create handle an unreachable point just like any other unavailable inventory, retaining held items.
        return vs$canReach(point) && original.call(point);
    }

    @WrapWithCondition(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmInteractionPoint;keepAlive()V"),
        remap = false
    )
    private boolean vs$keepReachableInputAlive(ArmInteractionPoint point) {
        return vs$canReach(point);
    }

    @Unique
    private boolean vs$canReach(ArmInteractionPoint point) {
        BlockPos targetPos = point.getPos();
        return level.getChunkSource().hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4)
            && MechanicalArmCompat.isWithinRange(level, worldPosition, targetPos, ArmBlockEntity.getRange());
    }

    @WrapOperation(
        method = "initInteractionPoints",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/mechanicalArm/ArmBlockEntity;isAreaActuallyLoaded(Lnet/minecraft/core/BlockPos;I)Z")
    )
    private boolean vs$waitForTargetChunks(ArmBlockEntity arm, BlockPos center, int range, Operation<Boolean> original) {
        if (!original.call(arm, center, range)) {
            return false;
        }
        // Create only waits for chunks around the arm. Cross-ship targets can be in distant shipyard chunks;
        // deserializing them before those chunks arrive would permanently discard the connections.
        for (Tag tag : interactionPointTag) {
            BlockPos targetPos = NbtUtils.readBlockPos(((CompoundTag) tag).getCompound("Pos")).offset(worldPosition);
            if (!level.getChunkSource().hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
                return false;
            }
        }
        return true;
    }
}
