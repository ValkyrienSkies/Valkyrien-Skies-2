package org.valkyrienskies.mod.mixin.feature.ai.goal.horse;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Vanilla AbstractHorse.aiStep checks the world block below for GRASS_BLOCK to trigger
// the eat animation; misses ship-mounted grass. Wrap returns ship-frame block when the
// world cell is air. Covers Horse/Donkey/Mule/Llama/TraderLlama/Skeleton/Zombie horses.
@Mixin(AbstractHorse.class)
public abstract class MixinAbstractHorseEatGrass {

    @Unique
    private static final ThreadLocal<Vector3d> VS$SRC = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$DEST = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            ordinal = 0
        )
    )
    private BlockState vs$shipyardGrassBelow(
        final Level level, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        final BlockState vanilla = original.call(level, worldPos);
        if (!vanilla.isAir()) return vanilla;

        final AbstractHorse horse = (AbstractHorse) (Object) this;
        final Ship ship = VSGameUtilsKt.getEnclosingShip(horse);
        if (ship == null) return vanilla;

        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            VS$SRC.get().set(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            VS$DEST.get()
        );
        final BlockState shipState = original.call(level, BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z));
        return shipState.isAir() ? vanilla : shipState;
    }
}
