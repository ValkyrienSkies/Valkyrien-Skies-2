package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

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
    public double includeShipsInBlockBreakDistanceCheck(final double g, final BlockPos pos,
        final PlayerActionC2SPacket.Action action,
        final Direction direction, final int worldHeight) {
        if (VSConfig.getEnableInteractDistanceChecks()) {
            final Vector3d blockCenter = VectorConversionsMCKt.toJOMLD(pos).add(0.5, 0.5, 0.5);
            return VSGameUtils.getWorldCoordinates(world, pos, blockCenter)
                .distanceSquared(player.getX(), player.getY() + 1.5, player.getZ());
        } else {
            return 0;
        }
    }
    
    @Inject(
        method = "interactBlock",
        at = @At("HEAD")
    )
    public void interactBlockTransform(final ServerPlayerEntity player, final World world, final ItemStack itemStack,
        final Hand hand, final BlockHitResult blockResult, final CallbackInfoReturnable<ActionResult> cir) {

        PlayerUtil.INSTANCE.storeAndTransformPlayer(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, blockResult.getBlockPos()));
    }

    @Inject(
        method = "interactBlock",
        at = @At("RETURN")
    )
    public void interactBlockTransformBack(
        final ServerPlayerEntity player, final World world, final ItemStack itemStack,
        final Hand hand, final BlockHitResult blockHitResult, final CallbackInfoReturnable<ActionResult> cir) {

        PlayerUtil.INSTANCE.restoreTransformedPlayer(player);
    }
}
