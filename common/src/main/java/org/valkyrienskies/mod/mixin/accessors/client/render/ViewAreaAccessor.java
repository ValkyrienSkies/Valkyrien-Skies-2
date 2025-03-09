package org.valkyrienskies.mod.mixin.accessors.client.render;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
    @Invoker(value = "getRenderSectionAt")
    SectionRenderDispatcher.RenderSection callGetRenderSectionAt(BlockPos pos);
}
