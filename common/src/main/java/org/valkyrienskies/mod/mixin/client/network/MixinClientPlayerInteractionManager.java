package org.valkyrienskies.mod.mixin.client.network;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(
        method = "interactBlock",
        at = @At("HEAD")
    )
    public void interactBlockTransform(final ClientPlayerEntity player, final ClientWorld world, final Hand hand,
        final BlockHitResult blockResult, final CallbackInfoReturnable<ActionResult> cir) {

        PlayerUtil.INSTANCE.storeAndTransformPlayer(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, blockResult.getBlockPos()));
    }

    @Inject(
        method = "interactBlock",
        at = @At("RETURN")
    )
    public void interactBlockTransformBack(final ClientPlayerEntity player, final ClientWorld clientWorld, final Hand hand,
        final BlockHitResult blockHitResult, final CallbackInfoReturnable<ActionResult> cir) {
        PlayerUtil.INSTANCE.restoreTransformedPlayer(player);
    }

}
