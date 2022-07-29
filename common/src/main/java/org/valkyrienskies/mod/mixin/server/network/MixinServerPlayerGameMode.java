package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.VSGameUtils;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameMode {

    @Shadow
    public ServerPlayer player;

    @Shadow
    public ServerLevel level;

    /**
     * Includes ships in server-side distance check when player breaks a block.
     */
    @ModifyVariable(
        method = "handleBlockBreakAction",
        at = @At("STORE"),
        index = 11
    )
    public double handleBlockBreakAction(final double g, final BlockPos pos,
        final ServerboundPlayerActionPacket.Action action,
        final Direction direction, final int worldHeight) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final Vector3d blockCenter = VectorConversionsMCKt.toJOMLD(pos).add(0.5, 0.5, 0.5);
            return VSGameUtils.getWorldCoordinates(level, pos, blockCenter)
                .distanceSquared(player.getX(), player.getY() + 1.5, player.getZ());
        } else {
            return 0;
        }
    }
}
