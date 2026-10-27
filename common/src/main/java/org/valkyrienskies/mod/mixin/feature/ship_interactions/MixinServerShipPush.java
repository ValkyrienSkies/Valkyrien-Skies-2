package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShipPushing;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerShipPush {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void vs$startPush(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand,
                              BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        if (ShipPushing.tryStart(player, hand, hit)) {
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
