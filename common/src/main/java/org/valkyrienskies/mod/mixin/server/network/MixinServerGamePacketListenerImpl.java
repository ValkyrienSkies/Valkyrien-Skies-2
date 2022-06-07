package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"
        )
    )
    public double includeShipsInBlockInteractDistanceCheck(
        final ServerPlayer receiver, final double x, final double y, final double z) {
        if (VSConfig.getEnableInteractDistanceChecks()) {
            return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
        } else {
            return 0;
        }
    }

    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        )
    )
    private InteractionResult wrapInteractBlock(final ServerPlayerGameMode receiver,
        final ServerPlayer serverPlayerEntity, final Level world, final ItemStack item,
        final InteractionHand hand, final BlockHitResult hitResult) {

        return PlayerUtil.INSTANCE.transformPlayerTemporarily(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, hitResult.getBlockPos()),
            () -> receiver.useItemOn(player, world, item, hand, hitResult));
    }

}
