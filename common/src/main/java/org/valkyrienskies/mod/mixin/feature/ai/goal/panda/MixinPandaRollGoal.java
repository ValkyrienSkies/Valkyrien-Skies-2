package org.valkyrienskies.mod.mixin.feature.ai.goal.panda;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Panda$PandaRollGoal.canUse triggers a roll when the world cell at blockPosition().offset(i, -1, j) is air; for a panda on a ship the world cell there is always air regardless of ship structure, so the goal misfires constantly. Substitute the projected ship-local cell so the air-check matches the geometry the panda is actually walking on.
@Mixin(targets = "net.minecraft.world.entity.animal.Panda$PandaRollGoal")
public abstract class MixinPandaRollGoal {

    @Shadow
    @Final
    private Panda panda;

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$readShipyardRollTargetCell(
        final Level level, final BlockPos worldPos, final Operation<BlockState> original
    ) {
        final BlockState vanilla = original.call(level, worldPos);
        if (!vanilla.isAir()) return vanilla;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(panda);
        if (ship == null) return vanilla;

        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            VS$OUT.get()
        );
        final BlockPos shipyardPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
        final BlockState shipState = original.call(level, shipyardPos);
        return shipState.isAir() ? vanilla : shipState;
    }
}
