package org.valkyrienskies.mod.mixin.accessors.client.render.vdex;

import java.util.Optional;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureStateShard.class)
public interface TextureStateShardAccessor {
    @Accessor
    Optional<ResourceLocation> getTexture();
}
