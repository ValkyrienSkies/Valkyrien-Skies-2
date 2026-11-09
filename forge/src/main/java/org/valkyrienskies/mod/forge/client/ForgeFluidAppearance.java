package org.valkyrienskies.mod.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.valkyrienskies.mod.common.fluid.FluidAppearance;

public final class ForgeFluidAppearance implements FluidAppearance.Provider {
    @Override
    public FluidAppearance resolve(final BlockAndTintGetter level, final BlockPos position, final FluidState fluid) {
        final var extension = IClientFluidTypeExtensions.of(fluid);
        final var still = extension.getStillTexture(fluid, level, position);
        final var flowing = extension.getFlowingTexture(fluid, level, position);
        if (still == null || flowing == null) return null;
        final var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final int tint = extension.getTintColor(fluid, level, position);
        return new FluidAppearance(atlas.apply(still), atlas.apply(flowing),
            ItemBlockRenderTypes.getRenderLayer(fluid) == RenderType.solid() ? tint | 0xFF000000 : tint,
            fluid.getFluidType().getLightLevel(fluid, level, position));
    }
}
