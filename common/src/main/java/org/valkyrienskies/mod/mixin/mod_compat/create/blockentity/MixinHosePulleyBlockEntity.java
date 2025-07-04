package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(HosePulleyBlockEntity.class)
public class MixinHosePulleyBlockEntity extends KineticBlockEntity {
    @Redirect(method = "lambda$new$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below(I)Lnet/minecraft/core/BlockPos;"))
    private BlockPos lambdaBelow(BlockPos blockPos, int i) {
        return valkyrienskies$worldAwareBelow(blockPos, i);
    }

    @Redirect(method = "onSpeedChanged",at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below(I)Lnet/minecraft/core/BlockPos;"))
    private BlockPos onSpeedChangedBelow(BlockPos instance, int i) {
        return valkyrienskies$worldAwareBelow(instance, i);
    }

    @Redirect(method = "tick",at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below(I)Lnet/minecraft/core/BlockPos;"))
    private BlockPos tickBelow(BlockPos blockPos, int i) {
        return valkyrienskies$worldAwareBelow(blockPos, i);
    }

    @Redirect(method = "lazyTick",at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below(I)Lnet/minecraft/core/BlockPos;"))
    private BlockPos lazyTickBelow(BlockPos blockPos, int i) {
        return valkyrienskies$worldAwareBelow(blockPos, i);
    }

    @Unique
    private BlockPos valkyrienskies$worldAwareBelow(BlockPos blockPos, int i) {
        BlockPos truePosition = blockPos.below(i);
        // If the hose is not obstructed by our own ship, check against world and other ships.
        AABB targetAABB = new AABB(truePosition);
        Ship hoseShip = VSGameUtilsKt.getShipManagingPos(level, worldPosition);
        if (hoseShip != null) {
            targetAABB = VSGameUtilsKt.transformAabbToWorld(level, targetAABB);
        }
        Iterable<Ship> ships = VSGameUtilsKt.getShipsIntersecting(level, targetAABB);
        // Even if many ships intersect our target position, we can only handle one.
        boolean foundShipPos = false;
        Iterator<Ship> shipIt = ships.iterator();
        if (shipIt.hasNext()) {
            Ship fluidShip = shipIt.next();
            if (fluidShip != null && fluidShip != hoseShip) {
                foundShipPos = true;
                targetAABB = VectorConversionsMCKt.toMinecraft(
                    VectorConversionsMCKt.toJOML(targetAABB).transform(fluidShip.getWorldToShip()));
            }
        }
        if (!foundShipPos) {
            // Peeking a bit further than we should. Necessary for regular hose functionality on own ship
            if (!level.getBlockState(truePosition).canBeReplaced() || !level.getBlockState(truePosition.below(1)).canBeReplaced()) {
                return truePosition;
            }
        }
        return BlockPos.containing(targetAABB.getCenter());
    }

    public MixinHosePulleyBlockEntity(BlockEntityType<?> typeIn, BlockPos pos,
        BlockState state) {
        super(typeIn, pos, state);
    }
}
