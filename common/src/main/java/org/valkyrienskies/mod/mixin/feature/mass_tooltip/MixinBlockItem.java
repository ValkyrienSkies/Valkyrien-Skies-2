package org.valkyrienskies.mod.mixin.feature.mass_tooltip;

import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.mixinducks.feature.mass_tooltip.MassTooltipVisibility;
import oshi.util.tuples.Pair;

@Mixin(BlockItem.class)
public class MixinBlockItem {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void ValkyrienSkies$addMassToTooltip(ItemStack itemStack, TooltipContext tooltipContext,
        List<Component> list, TooltipFlag tooltipFlag, CallbackInfo ci) {
        final MassTooltipVisibility visibility = VSGameConfig.CLIENT.getTooltip().getMassTooltipVisibility();
        if (visibility.isVisible(tooltipFlag)) {
            try {
                final BlockItem item = (BlockItem) itemStack.getItem();
                final Double mass =
                    Objects.requireNonNull(BlockStateInfo.INSTANCE.get(item.getBlock().defaultBlockState()))
                        .getFirst();
                list.add(Component.translatable("tooltip.valkyrienskies.mass")
                    .append(VSGameConfig.CLIENT.getTooltip().getUseImperialUnits() ?
                        getImperialText(mass) : ": " + mass + "kg").withStyle(ChatFormatting.DARK_GRAY));
            } catch (final Exception ignored) {
            }
        }
    }

    @Unique
    private Pair<Integer, Integer> convertToImperial(final double mass) {
        final double ounces = mass * 35.274;
        final double pounds = Math.floor(ounces / 16);
        return new Pair<>(
            (int) pounds,
            (int) Math.floor((ounces / 16 - pounds) * 16)
        );
    }

    @Unique
    private String getImperialText(final double mass) {
        String impText = ": ";
        final Pair<Integer, Integer> imperial = convertToImperial(mass);
        if (imperial.getA() > 0) {
            impText = impText + imperial.getA();
            if (imperial.getA() == 1) {
                impText = impText + "lb. ";
            } else {
                impText = impText + "lbs. ";
            }
        }

        if (imperial.getB() > 0) {
            impText = impText + imperial.getB() + "oz.";
        }

        return impText;
    }
}
