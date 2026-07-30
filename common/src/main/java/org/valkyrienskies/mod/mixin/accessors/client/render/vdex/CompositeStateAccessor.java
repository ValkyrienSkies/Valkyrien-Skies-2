package org.valkyrienskies.mod.mixin.accessors.client.render.vdex;

import net.minecraft.client.renderer.RenderStateShard.EmptyTextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor
    EmptyTextureStateShard getTextureState();
}
