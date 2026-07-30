package org.valkyrienskies.mod.mixin.accessors.client.render.vdex;

import net.minecraft.client.renderer.RenderType.CompositeRenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompositeRenderType.class)
public interface CompositeRenderTypeAccessor {
    @Accessor
    CompositeState getState();
}
