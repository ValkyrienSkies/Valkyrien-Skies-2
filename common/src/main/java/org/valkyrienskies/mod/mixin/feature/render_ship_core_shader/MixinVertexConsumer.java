package org.valkyrienskies.mod.mixin.feature.render_ship_core_shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VertexConsumer.class)
public interface MixinVertexConsumer {
    // Our ridiculously bright shade has made it through and now will be passed to shader as Color.a = 0.
    // Sadly, the Mixin version used in Forge 1.20.1 does not support injecting into default interfaces.
    // Mostly a copy of the original method. Will need an updated once we switch versions.
    default void putBulkData(PoseStack.Pose pose, BakedQuad bakedQuad, float[] fs, float f, float g, float h, int[] is,
        int i, boolean bl) {
        float[] gs = new float[] {fs[0], fs[1], fs[2], fs[3]};
        int[] js = new int[] {is[0], is[1], is[2], is[3]};
        int[] ks = bakedQuad.getVertices();
        Vec3i vec3i = bakedQuad.getDirection().getNormal();
        Matrix4f matrix4f = pose.pose();
        Vector3f vector3f =
            pose.normal().transform(new Vector3f((float) vec3i.getX(), (float) vec3i.getY(), (float) vec3i.getZ()));
        int j = 8;
        int k = ks.length / 8;

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = memoryStack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer intBuffer = byteBuffer.asIntBuffer();

            for (int l = 0; l < k; ++l) {
                intBuffer.clear();
                intBuffer.put(ks, l * 8, 8);
                float m = byteBuffer.getFloat(0);
                float n = byteBuffer.getFloat(4);
                float o = byteBuffer.getFloat(8);
                float s;
                float t;
                float u;
                // Extract our +4f and restore intended shade value
                boolean smuggledNoShade = false;
                float a = gs[l];
                if (a > 4f) {
                    a -= 4f;
                    smuggledNoShade = true;
                }
                if (bl) {
                    float p = (float) (byteBuffer.get(12) & 255) / 255.0F;
                    float q = (float) (byteBuffer.get(13) & 255) / 255.0F;
                    float r = (float) (byteBuffer.get(14) & 255) / 255.0F;
                    s = p * a * f;
                    t = q * a * g;
                    u = r * a * h;
                } else {
                    s = a * f;
                    t = a * g;
                    u = a * h;
                }

                int v = js[l];
                float q = byteBuffer.getFloat(16);
                float r = byteBuffer.getFloat(20);
                Vector4f vector4f = matrix4f.transform(new Vector4f(m, n, o, 1.0F));
                // The vertex is colored as alpha = 0, which is used in shader as a "no shade" marker.
                ((VertexConsumer)this).vertex(vector4f.x(), vector4f.y(), vector4f.z(), s, t, u, smuggledNoShade ? 0f : 1f, q, r, i, v, vector3f.x(),
                    vector3f.y(), vector3f.z());
            }
        }
    }
}
