package org.valkyrienskies.mod.compat.sodium;

import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class SodiumFluidSprites {
    private SodiumFluidSprites() { }

    public static void markActive(final Iterable<TextureAtlasSprite> sprites) {
        for (final var sprite : sprites) SpriteUtil.markSpriteActive(sprite);
    }
}
