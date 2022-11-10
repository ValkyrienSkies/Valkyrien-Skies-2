package org.valkyrienskies.mod.forge.mixin.feature.forge_interact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;canInteractWith(Lnet/minecraft/core/BlockPos;D)Z"
        )
    )
    public boolean skipForgeDistanceCheck(final ServerPlayer receiver, final BlockPos pos, final double padding) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final double reach = receiver.getReachDistance() + padding;
            final Vec3 eyes = receiver.getEyePosition();
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(receiver.level,
                eyes.x, eyes.y, eyes.z,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            ) < reach * reach;
        } else {
            return true;
        }
    }
}
