package org.valkyrienskies.mod.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformFloat3v;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ShaderBindingContext;

import org.valkyrienskies.mod.compat.sodium.light.GlUniformInt3v;

/**
 * ChunkShaderInterface for the VS-modified world chunk shader. Rendered for
 * non-ship chunks when {@code dynamicShipToWorldLighting} is enabled and the
 * VsWorldFromShipLightStorage has tracked sections, so ships above the world
 * cast shadows and ship-internal emitters illuminate world blocks beneath them.
 */
public class WorldThing extends ChunkShaderInterface {
    private final GlUniformInt3v uniformRenderOrigin;
    private final GlUniformFloat3v uniformCameraFrac;
    private final GlUniformInt uniformShipEmitters;
    private final GlUniformInt uniformShipEmitterCount;
    private final GlUniformInt uniformShipOccluders;
    private final GlUniformInt uniformShipOccluderCount;

    public WorldThing(ShaderBindingContext context, ChunkShaderOptions options) {
        super(context, options);
        this.uniformRenderOrigin = context.bindUniform("u_VsRenderOrigin", GlUniformInt3v::new);
        this.uniformCameraFrac = context.bindUniform("u_VsCameraFrac", GlUniformFloat3v::new);
        this.uniformShipEmitters = context.bindUniform("u_VsShipEmitters", GlUniformInt::new);
        this.uniformShipEmitterCount = context.bindUniform("u_VsShipEmitterCount", GlUniformInt::new);
        this.uniformShipOccluders = context.bindUniform("u_VsShipOccluders", GlUniformInt::new);
        this.uniformShipOccluderCount = context.bindUniform("u_VsShipOccluderCount", GlUniformInt::new);
    }

    public void setRenderOrigin(int x, int y, int z) {
        this.uniformRenderOrigin.set(x, y, z);
    }

    public void setCameraFrac(float fx, float fy, float fz) {
        this.uniformCameraFrac.set(fx, fy, fz);
    }

    public void setShipEmitters(int textureUnit, int count) {
        this.uniformShipEmitters.setInt(textureUnit);
        this.uniformShipEmitterCount.setInt(count);
    }

    public void setShipOccluders(int textureUnit, int count) {
        this.uniformShipOccluders.setInt(textureUnit);
        this.uniformShipOccluderCount.setInt(count);
    }
}
