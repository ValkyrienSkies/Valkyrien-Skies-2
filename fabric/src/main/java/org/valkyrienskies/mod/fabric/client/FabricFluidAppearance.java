package org.valkyrienskies.mod.fabric.client;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.valkyrienskies.mod.common.fluid.FluidAppearance;

public final class FabricFluidAppearance implements FluidAppearance.Provider {
    @Override
    public FluidAppearance resolve(final BlockAndTintGetter level, final BlockPos position, final FluidState fluid) {
        final var handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid.getType());
        if (handler == null) return null;
        final var sprites = handler.getFluidSprites(level, position, fluid);
        if (sprites == null || sprites.length < 2 || sprites[0] == null || sprites[1] == null) return null;
        return new FluidAppearance(sprites[0], sprites[1], handler.getFluidColor(level, position, fluid) | 0xFF000000,
            fluid.createLegacyBlock().getLightEmission());
    }
}
