package org.valkyrienskies.mod.mixin.feature.mass_tooltip;


import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.ClientBlockStateInfo;
import org.valkyrienskies.mod.common.blockstate.SolidStateProperties;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.config.VSGameConfig.Client.TOOLTIP;
import org.valkyrienskies.mod.common.item.MassTooltipHelperKt;

@Mixin(BlockItem.class)
public class MixinBlockItem {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void valkyrienskies$addMassToTooltip(final ItemStack itemStack, final Level level,
        final List<Component> list, final TooltipFlag tooltipFlag, final CallbackInfo ci) {
        if (!ClientBlockStateInfo.INSTANCE.getClientHasMassInfo()) return;
        final TOOLTIP tooltip = VSGameConfig.CLIENT.getTooltip();
        if (tooltip.getMassTooltipVisibility().isVisible(tooltipFlag)) {
            final SolidStateProperties props = ClientBlockStateInfo.INSTANCE.getSolidProperties(((BlockItem) itemStack.getItem()).getBlock().defaultBlockState());
            final double mass = props != null ? props.getMass() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultMass(), 404.0);
            list.add(MassTooltipHelperKt.makeMassComponent(mass, false, tooltip.getUseImperialUnits(), tooltip.getDetailedMassTooltip()));
        }
    }
}
