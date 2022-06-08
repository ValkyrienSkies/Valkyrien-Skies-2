package org.valkyrienskies.mod.mixin.accessors.client.render;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * This mixin lets us get and set private fields in {@link SheetedDecalTextureGenerator}.
 */
@Mixin(SheetedDecalTextureGenerator.class)
public interface OverlayVertexConsumerAccessor {
    @Accessor("delegate")
    VertexConsumer getDelegate();

    @Accessor("cameraInversePose")
    void setTextureMatrix(Matrix4f textureMatrix);

    @Accessor("normalInversePose")
    void setNormalMatrix(Matrix3f normalMatrix);
}
