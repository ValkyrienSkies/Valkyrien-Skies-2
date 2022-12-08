package org.valkyrienskies.mod.mixin.accessors.client.render;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * This mixin lets us get and set private fields in {@link SheetedDecalTextureGenerator}.
 */
@Mixin(SheetedDecalTextureGenerator.class)
public interface OverlayVertexConsumerAccessor {
    @Accessor("delegate")
    VertexConsumer getDelegate();
}
