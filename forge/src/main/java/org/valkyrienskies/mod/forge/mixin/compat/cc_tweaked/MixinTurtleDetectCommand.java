package org.valkyrienskies.mod.forge.mixin.compat.cc_tweaked;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.shared.turtle.core.InteractDirection;
import dan200.computercraft.shared.turtle.core.TurtleDetectCommand;
import dan200.computercraft.shared.util.WorldUtil;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(TurtleDetectCommand.class)
public class MixinTurtleDetectCommand {
    @Shadow
    private InteractDirection direction;

    @Inject(method = "execute", at = @At("RETURN"), remap = false, cancellable = true)
    public void ValkyrienSkies2$execute(@Nonnull ITurtleAccess turtle, CallbackInfoReturnable cir) {
        Direction direction = this.direction.toWorldDir(turtle);
        Level world = turtle.getWorld();
        BlockPos newPosition = turtle.getPosition().relative(direction);

        List<Vector3d> detectedShips = VSGameUtilsKt.transformToNearbyShipsAndWorld(world, newPosition.getX(), newPosition.getY(), newPosition.getZ(), 0.5);

        for (Vector3d vec : detectedShips) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(world, vec);
            if (ship != null) {
                Object[] results = new Object[2];
                results[0] = "ship";

                Vector3d vecPos = ship.getWorldToShip().transformPosition(vec);
                BlockPos pos = new BlockPos(vecPos.x, vecPos.y, vecPos.z);
                if (!WorldUtil.isLiquidBlock(world, pos) && !world.isEmptyBlock(pos)) {
                    results[1] = "empty";
                } else {
                    results[1] = "solid";
                }

                cir.setReturnValue(TurtleCommandResult.success(results));
            }
        }
    }
}
