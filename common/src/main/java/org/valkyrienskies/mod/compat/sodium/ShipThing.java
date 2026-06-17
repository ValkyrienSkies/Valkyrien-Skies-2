package org.valkyrienskies.mod.compat.sodium;

import org.joml.Matrix4fc;

import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ShaderBindingContext;
import org.valkyrienskies.mod.compat.sodium.light.GlUniformInt3v;

/**
 * VS-specific chunk shader interface. Mirrors the per-feature {@code #ifdef}
 * gating in the VSH/FSH: each uniform is only bound when the shader actually
 * declares it, because sodium's {@link me.jellysquid.mods.sodium.client.gl.shader.GlProgram#bindUniform}
 * throws when a uniform name is missing from the linked program.
 *
 * <p>Feature bits are passed in from {@link SodiumCompat} at link time and
 * mirror what the shader was compiled with.
 */
public class ShipThing extends ChunkShaderInterface {
    // Ship-to-world matrix; only needed when the shader transforms the per-quad
    // face normal to world space (i.e. when shading or world-light lookup is on).
    private final GlUniformMatrix4f uniformTransformMatrix;
    // Local-to-camera-relative matrix; only needed when the VSH emits world
    // position for lighting/AO or samples world biome (uses worldPosVertex).
    private final GlUniformMatrix4f uniformLocalToCameraRel;
    // Integer camera origin; same conditions as u_LocalToCameraRel.
    private final GlUniformInt3v uniformRenderOrigin;
    // World-light buffer textures; only when VS_DYNAMIC_LIGHT.
    private final GlUniformInt uniformLightSections;
    private final GlUniformInt uniformLightLut;
    // World-biome buffer textures; only when VS_DYNAMIC_BIOME.
    private final GlUniformInt uniformBiomeSections;
    private final GlUniformInt uniformBiomeLut;
    // World-from-ship buffer textures; only when VS_SHIP_ON_SHIP. Same data
    // the world chunk shader queries so one ship can shadow / illuminate
    // another ship that happens to be nearby in world space.
    private final GlUniformInt uniformShipEmitters;
    private final GlUniformInt uniformShipEmitterCount;
    // Same per-frame solid-voxel list the world FSH uses for ship-to-world
    // AO; binding it on the ship shader too lets one ship's voxels shadow
    // another ship's surface (and the same ship's own concave geometry).
    private final GlUniformInt uniformShipOccluders;
    private final GlUniformInt uniformShipOccluderCount;

    public ShipThing(ShaderBindingContext context, ChunkShaderOptions options, int features) {
        super(context, options);
        boolean light = (features & SodiumCompat.FEATURE_LIGHT) != 0;
        boolean biome = (features & SodiumCompat.FEATURE_BIOME) != 0;
        boolean shade = (features & SodiumCompat.FEATURE_SHADE) != 0;
        boolean shipOnShip = (features & SodiumCompat.FEATURE_SHIP_ON_SHIP) != 0;
        boolean wantTransform = light || shade || shipOnShip;
        boolean wantLocalToCamera = light || biome || shipOnShip;

        this.uniformTransformMatrix = wantTransform
                ? context.bindUniform("u_TransformMatrix", GlUniformMatrix4f::new) : null;
        this.uniformLocalToCameraRel = wantLocalToCamera
                ? context.bindUniform("u_LocalToCameraRel", GlUniformMatrix4f::new) : null;
        this.uniformRenderOrigin = wantLocalToCamera
                ? context.bindUniform("u_VsRenderOrigin", GlUniformInt3v::new) : null;
        this.uniformLightSections = light
                ? context.bindUniform("u_VsLightSections", GlUniformInt::new) : null;
        this.uniformLightLut = light
                ? context.bindUniform("u_VsLightLut", GlUniformInt::new) : null;
        this.uniformBiomeSections = biome
                ? context.bindUniform("u_VsBiomeSections", GlUniformInt::new) : null;
        this.uniformBiomeLut = biome
                ? context.bindUniform("u_VsBiomeLut", GlUniformInt::new) : null;
        this.uniformShipEmitters = shipOnShip
                ? context.bindUniform("u_VsShipEmitters", GlUniformInt::new) : null;
        this.uniformShipEmitterCount = shipOnShip
                ? context.bindUniform("u_VsShipEmitterCount", GlUniformInt::new) : null;
        this.uniformShipOccluders = shipOnShip
                ? context.bindUniform("u_VsShipOccluders", GlUniformInt::new) : null;
        this.uniformShipOccluderCount = shipOnShip
                ? context.bindUniform("u_VsShipOccluderCount", GlUniformInt::new) : null;
    }

    public void setTransformMatrix(Matrix4fc matrix) {
        if (this.uniformTransformMatrix != null) this.uniformTransformMatrix.set(matrix);
    }

    public void setLocalToWorldMatrix(Matrix4fc matrix) {
        if (this.uniformLocalToCameraRel != null) this.uniformLocalToCameraRel.set(matrix);
    }

    public void setRenderOrigin(int x, int y, int z) {
        if (this.uniformRenderOrigin != null) this.uniformRenderOrigin.set(x, y, z);
    }

    public void setLightSectionsSampler(int unit) {
        if (this.uniformLightSections != null) this.uniformLightSections.setInt(unit);
    }

    public void setLightLutSampler(int unit) {
        if (this.uniformLightLut != null) this.uniformLightLut.setInt(unit);
    }

    public void setBiomeSectionsSampler(int unit) {
        if (this.uniformBiomeSections != null) this.uniformBiomeSections.setInt(unit);
    }

    public void setBiomeLutSampler(int unit) {
        if (this.uniformBiomeLut != null) this.uniformBiomeLut.setInt(unit);
    }

    public void setShipEmitters(int textureUnit, int count) {
        if (this.uniformShipEmitters != null) this.uniformShipEmitters.setInt(textureUnit);
        if (this.uniformShipEmitterCount != null) this.uniformShipEmitterCount.setInt(count);
    }

    public void setShipOccluders(int textureUnit, int count) {
        if (this.uniformShipOccluders != null) this.uniformShipOccluders.setInt(textureUnit);
        if (this.uniformShipOccluderCount != null) this.uniformShipOccluderCount.setInt(count);
    }
}
