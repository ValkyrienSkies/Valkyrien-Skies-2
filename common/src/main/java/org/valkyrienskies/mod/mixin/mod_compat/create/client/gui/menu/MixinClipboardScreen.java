package org.valkyrienskies.mod.mixin.mod_compat.create.client.gui.menu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ClipboardScreen.class)
public class MixinClipboardScreen {
    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
        )
    )
    // Fixes the clipboard crashing on typing on a ship
    private static boolean injectCloserThan(BlockPos instance, Vec3i vec3i, double v, Operation<Boolean> original) {
        // ClientLevel
        Level level = Minecraft.getInstance().level;

        // The instance is the blockpos of the player, the vec3i is the position in the shipyard
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, new BlockPos(vec3i));

        // If we're on a ship, move the player pos to the shipyard before the distance check
        if (ship != null) {

            Vector3d playerPosVec = VectorConversionsMCKt.toJOML(instance.getCenter());
            ship.getTransform().getWorldToShip().transformPosition(playerPosVec);

            // Do the calculation without 'original', so we don't have to deal with precision loss in going back to ints
            return playerPosVec.distanceSquared(new Vector3d(VectorConversionsMCKt.toJOML(vec3i))) < Mth.square(v);
        } else {
            return original.call(instance, vec3i, v);
        }
    }
}
