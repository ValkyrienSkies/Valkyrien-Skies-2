package org.valkyrienskies.mod.mixin.client.renderer;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SheetedDecalTextureGenerator.class)
public interface SheetedDecalTextureGeneratorAccessor {
    @Accessor("delegate")
    VertexConsumer getDelegate();
}
