package org.valkyrienskies.mod.common.fluid;

import java.util.ServiceLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;

public record FluidAppearance(TextureAtlasSprite still, TextureAtlasSprite flowing, int tint, int emission) {
    public interface Provider {
        FluidAppearance resolve(BlockAndTintGetter level, BlockPos position, FluidState fluid);
    }

    public static FluidAppearance resolve(final BlockAndTintGetter level, final BlockPos position, final FluidState fluid) {
        return Platform.PROVIDER.resolve(level, position, fluid);
    }

    public int light(final int sampled) {
        return (sampled & 0xFFFF0000) | Math.max(sampled & 0xFFFF, Math.max(0, Math.min(15, emission)) << 4);
    }

    public float red() { return ((tint >>> 16) & 255) / 255.0F; }
    public float green() { return ((tint >>> 8) & 255) / 255.0F; }
    public float blue() { return (tint & 255) / 255.0F; }
    public float alpha() { return ((tint >>> 24) & 255) / 255.0F; }

    private static final class Platform {
        private static final Provider PROVIDER = ServiceLoader.load(Provider.class).findFirst().orElseThrow();
    }
}
