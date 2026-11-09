package org.valkyrienskies.mod.compat.flooding.shaders;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.material.FluidState;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.iris.IrisCompat;

public final class NativeFluidCompat {
    public static boolean drawing;
    private static final java.util.Map<Integer, Integer> SIM_UNIFORMS = new java.util.HashMap<>();
    private NativeFluidCompat() { }

    public static boolean active() {
        if (!LoadedMods.getIris() || !IrisCompat.isIrisShaderActive()) return false;
        return complementarySelected() || photonSelected() || blissSelected() || derivativeSelected()
            || iterationRpSelected() || bslSelected();
    }

    public static boolean complementarySelected() {
        final String name = Iris.getCurrentPackName();
        return "ComplementaryReimagined_r5.9.3.zip".equals(name)
            || "ComplementaryUnbound_r5.9.3.zip".equals(name);
    }

    public static boolean photonSelected() {
        return "photon_v1.4.zip".equals(Iris.getCurrentPackName());
    }

    public static boolean blissSelected() {
        return "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip".equals(Iris.getCurrentPackName());
    }

    public static boolean derivativeSelected() {
        return "Derivative-Shaders-All-Versions.zip".equals(Iris.getCurrentPackName());
    }

    public static boolean iterationRpSelected() {
        return "IterationRP-Shaders-v0.8.7.zip".equals(Iris.getCurrentPackName());
    }

    public static boolean bslSelected() {
        return "BSL_v10.1.3.zip".equals(Iris.getCurrentPackName());
    }

    public static ShaderInstance shader(final boolean opaque) {
        if (!active() || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline)) return null;
        return pipeline.getShaderMap().getShader(opaque ? ShaderKey.TERRAIN_SOLID : ShaderKey.TERRAIN_TRANSLUCENT);
    }

    public static void beginFluid(final BufferBuilder builder, final FluidState fluid,
        final int x, final int y, final int z) {
        final var ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        final int id = fluid == null || ids == null ? -1 : ids.getInt(fluid.createLegacyBlock());
        ((BlockSensitiveBufferBuilder) builder).beginBlock(id, (byte) 1,
            (byte) (fluid == null ? 0 : fluid.createLegacyBlock().getLightEmission()), x, y, z);
    }

    public static void endFluid(final BufferBuilder builder) {
        ((BlockSensitiveBufferBuilder) builder).endBlock();
    }

    public static void interfaceFace(final BufferBuilder builder, final boolean value) {
        final var extended = (net.irisshaders.iris.vertices.IrisExtendedBufferBuilder) builder;
        ((BlockSensitiveBufferBuilder) builder).beginBlock(extended.iris$currentBlock(), (byte) (value ? 2 : 1), (byte) 0,
            extended.iris$currentLocalPosX(), extended.iris$currentLocalPosY(), extended.iris$currentLocalPosZ());
    }

    public static void apply(final ShaderInstance shader) {
        if (!active()) return;
        final int location = SIM_UNIFORMS.computeIfAbsent(shader.getId(),
            program -> org.lwjgl.opengl.GL20.glGetUniformLocation(program, "vs_SimFluid"));
        if (location >= 0) org.lwjgl.opengl.GL20.glUniform1i(location, drawing ? 1 : 0);
    }

    public static void clear() {
        SIM_UNIFORMS.clear();
        drawing = false;
    }
}
