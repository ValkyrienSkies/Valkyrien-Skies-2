package org.valkyrienskies.mod.mixin.accessors.client.render;

import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * This mixin lets us get and set private fields in {@link OverlayVertexConsumer}.
 */
@Mixin(OverlayVertexConsumer.class)
public interface OverlayVertexConsumerAccessor {
    @Accessor("vertexConsumer")
    VertexConsumer getVertexConsumer();

    @Accessor("textureMatrix")
    void setTextureMatrix(Matrix4f textureMatrix);

    @Accessor("normalMatrix")
    void setNormalMatrix(Matrix3f normalMatrix);
}
