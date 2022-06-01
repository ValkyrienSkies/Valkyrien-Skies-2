package org.valkyrienskies.mod.mixin.server.network;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "onPlayerInteractBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;squaredDistanceTo(DDD)D"
        )
    )
    public double includeShipsInBlockInteractDistanceCheck(
        final ServerPlayerEntity receiver, final double x, final double y, final double z) {
        if (VSConfig.getEnableInteractDistanceChecks()) {
            return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
        } else {
            return 0;
        }
    }

    @Redirect(
        method = "onPlayerInteractBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"
        )
    )
    private ActionResult wrapInteractBlock(final ServerPlayerInteractionManager receiver,
        final ServerPlayerEntity serverPlayerEntity, final World world, final ItemStack item,
        final Hand hand, final BlockHitResult hitResult) {

        return PlayerUtil.INSTANCE.transformPlayerTemporarily(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, hitResult.getBlockPos()),
            () -> receiver.interactBlock(player, world, item, hand, hitResult));
    }

}
