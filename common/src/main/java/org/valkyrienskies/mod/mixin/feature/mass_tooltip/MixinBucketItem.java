package org.valkyrienskies.mod.mixin.feature.mass_tooltip;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.client.ClientBlockStateInfo;
import org.valkyrienskies.mod.common.blockstate.LiquidStateProperties;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.config.VSGameConfig.Client.TOOLTIP;
import javax.annotation.ParametersAreNonnullByDefault;
import org.valkyrienskies.mod.common.item.MassTooltipHelperKt;

@Mixin(BucketItem.class)
public abstract class MixinBucketItem extends Item {
    public MixinBucketItem(Properties p){super(p);}

    @Shadow @Final private Fluid content;

    @Override
    @ParametersAreNonnullByDefault
    public void appendHoverText(ItemStack itemStack, @Nullable Level level, List<Component> list, TooltipFlag tooltipFlag) {
        if (!ClientBlockStateInfo.INSTANCE.getClientHasMassInfo()) return;
        final TOOLTIP tooltip = VSGameConfig.CLIENT.getTooltip();
        if (tooltip.getMassTooltipVisibility().isVisible(tooltipFlag) && content != null) {
            if (content instanceof EmptyFluid) return;
            final LiquidStateProperties props = ClientBlockStateInfo.INSTANCE.getLiquidProperties(content.defaultFluidState());
            final double density = props != null ? props.getDensity() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultDensity(), 404.0);
            list.add(MassTooltipHelperKt.makeMassComponent(density, true, tooltip.getUseImperialUnits(), tooltip.getDetailedMassTooltip()));
        }
    }
}
