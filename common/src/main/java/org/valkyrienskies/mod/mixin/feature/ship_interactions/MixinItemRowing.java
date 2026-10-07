package org.valkyrienskies.mod.mixin.feature.ship_interactions;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.ShovelRowing;

@Mixin(Item.class)
public class MixinItemRowing {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void vs$row(final Level level, final Player player, final InteractionHand hand,
        final CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (ShovelRowing.tryStroke(player, hand)) {
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide));
        }
    }
}
