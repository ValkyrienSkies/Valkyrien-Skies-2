package org.valkyrienskies.mod.mixin.accessors.client.render;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Accessor("sortOnUpload")
    boolean vs$sortOnUpload();
}
