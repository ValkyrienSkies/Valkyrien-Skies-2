package org.valkyrienskies.mod.mixin.client.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ItemTooltipsEvent;

@Mixin(Item.class)
public class MixinItem {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void ValkyrienSkies$triggerTooltipEvent(final ItemStack itemStack, final Level level,
        final List<Component> list, final TooltipFlag tooltipFlag, final CallbackInfo ci) {
        final VSGameEvents.ItemTooltipsEvent data = new ItemTooltipsEvent(
            itemStack, level, list, tooltipFlag
        );
        VSGameEvents.INSTANCE.getItemTooltips().emit(data);
    }
}
