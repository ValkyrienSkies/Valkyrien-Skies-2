package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class MixinBlockBreakingKineticTileEntity extends BlockEntity {

    public MixinBlockBreakingKineticTileEntity(BlockEntityType<?> blockEntityType,
        BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;getBreakingPos()Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos wrapBlockPos(BlockBreakingKineticBlockEntity instance, Operation<BlockPos> original) {
        BlockPos originalBP = original.call(instance);

        AABB searchAABB = new AABB(originalBP);

        // If we have an actual block in front of us, use that before anything else.
        if (!level.getBlockState(originalBP).isAir()) {
            return originalBP;
        }

        // region This offset stuff is a hack to prevent drills being able to drill 2 blocks in front.
        // We hardcode the offset that both the saw and the drill have, and hope there's no other BlockBreakingKineticBlockEntitys

        // Shrink by the 2/16 pixels the saw/drill don't have
        searchAABB = searchAABB.deflate(2.0/16);

        // atLowerCornerOf turns Vec3i to Vec3 without any offset being added
        Vec3 moveDirection = Vec3.atLowerCornerOf(
            getBlockState().getValue(DirectionalKineticBlock.FACING).getNormal());

        // Move the AABB backwards 2/16 pixels distance towards the saw/drill
        moveDirection = moveDirection.scale(-(2.0/16.0));
        searchAABB = searchAABB.move(moveDirection);

        // endregion

        searchAABB = VSGameUtilsKt.transformAabbToWorld(level, searchAABB);

        Ship originalShip = VSGameUtilsKt.getShipManagingPos(level, originalBP);

        List<BlockPos> foundSolids = new ArrayList<>();

        VSGameUtilsKt.transformFromWorldToNearbyShipsAndWorld(level, searchAABB.deflate(1.0E-6), (newAABB) -> {
            BlockPos.betweenClosedStream(newAABB).forEach((potentialFoundPos -> {
                // Don't use the special AABB check if on the same ship, originalBP will take care of that
                if (VSGameUtilsKt.getShipManagingPos(level, potentialFoundPos) == originalShip) return;
                if (!level.getBlockState(potentialFoundPos).isAir() && (!potentialFoundPos.equals(getBlockPos()))) {
                    foundSolids.add(potentialFoundPos.immutable());
                }
            }));
        });

        Vec3 center = VSGameUtilsKt.toWorldCoordinates(level, originalBP.getCenter());

        // Sort all found block positions by their distance to the originalBP with all coordinates being in world space
        foundSolids.sort(Comparator.comparingDouble(pos -> VSGameUtilsKt.toWorldCoordinates(level, pos.getCenter()).distanceTo(center)));

        // Get rid of positions too far from the main block
        // 1.5 is based on vibes
        foundSolids.removeIf(pos -> VSGameUtilsKt.toWorldCoordinates(level, pos.getCenter()).distanceTo(center) > 1.5);

        // if originalBP was air, AND all other locations were air, we still have to return something
        if (foundSolids.isEmpty()) {
            return originalBP;
        }

        return foundSolids.get(0);
    }

}

