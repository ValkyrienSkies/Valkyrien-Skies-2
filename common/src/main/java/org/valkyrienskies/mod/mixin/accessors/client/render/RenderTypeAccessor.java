package org.valkyrienskies.mod.mixin.accessors.client.render;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Invoker
    static CompositeState invokeTranslucentState(RenderStateShard.ShaderStateShard state) {
        return null;
    }
}
