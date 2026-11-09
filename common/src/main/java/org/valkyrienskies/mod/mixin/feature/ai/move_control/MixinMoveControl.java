package org.valkyrienskies.mod.mixin.feature.ai.move_control;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

// MoveControl.tick's MOVE_TO step-up scan reads world blocks at the mob's feet and conditionally jumps; ship-mounted mobs see world air there (deck lives in shipyard chunks) and the world-frame dy/dxz heuristic misfires on flat ship surfaces.
@Mixin(MoveControl.class)
public abstract class MixinMoveControl {

    @Shadow
    @Final
    protected Mob mob;

    // Force AIR for the step-up scan's collision-shape gate so a ship-mounted mob doesn't read whatever happens to be in the world cell at its feet.
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState vs$skipBlockBelowWhenOnShip(
        final Level level, final BlockPos pos, final Operation<BlockState> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(level, pos);
        try {
            if (vs$mobOnShip()) {
                return Blocks.AIR.defaultBlockState();
            }
        } catch (final Throwable t) {
            // fall through to vanilla
        }
        return original.call(level, pos);
    }

    // Only let the MOVE_TO step-up jump fire when something actually blocks horizontal motion; vanilla's dy/dxz heuristic drifts on ship-mounted mobs.
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/JumpControl;jump()V"
        )
    )
    private void vs$gateJumpOnCollisionForShipMobs(
        final JumpControl jumpControl, final Operation<Void> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) {
            original.call(jumpControl);
            return;
        }
        try {
            if (vs$mobOnShip() && !this.mob.horizontalCollision) {
                return;
            }
        } catch (final Throwable t) {
            // fall through to vanilla
        }
        original.call(jumpControl);
    }

    @Unique
    private boolean vs$mobOnShip() {
        if (mob instanceof IEntityDraggingInformationProvider provider) {
            final EntityDraggingInformation info = provider.getDraggingInformation();
            return info.isEntityBeingDraggedByAShip();
        }
        return false;
    }
}
