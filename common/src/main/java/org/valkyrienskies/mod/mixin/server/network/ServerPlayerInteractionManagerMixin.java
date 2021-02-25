package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.VSGameUtils;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    public ServerWorld world;

    /**
     * Includes ships in server-side distance check when player breaks a block.
     */
    @ModifyVariable(
        method = "processBlockBreakingAction",
        at = @At("STORE"),
        index = 11
    )
    public double modifyBlockBreakDistance(double g, BlockPos pos,
                                           PlayerActionC2SPacket.Action action,
                                           Direction direction, int worldHeight) {
        return VSGameUtils.getWorldCoordinates(world, pos)
            .add(0.5, 2.5, 0.5)
            .distanceSquared(player.getX(), player.getY(), player.getZ());
    }
}
