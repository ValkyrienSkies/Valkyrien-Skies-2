package org.valkyrienskies.mod.mixin.feature.mass_tooltip;

import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.BlockStateInfo;

@Mixin(BlockItem.class)
public class MixinBlockItem {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void ValkyrienSkies$addMassToTooltip(final ItemStack itemStack, final Level level,
        final List<Component> list, final TooltipFlag tooltipFlag, final CallbackInfo ci) {
        try {
            final BlockItem item = (BlockItem) itemStack.getItem();
            final Double mass = Objects.requireNonNull(BlockStateInfo.INSTANCE.get(item.getBlock().defaultBlockState()))
                .getFirst();
            list.add(new TranslatableComponent("tooltip.valkyrienskies.mass").append(
                ": " + mass).withStyle(ChatFormatting.DARK_GRAY));
        } catch (final Exception ignored) {

        }
    }
}
