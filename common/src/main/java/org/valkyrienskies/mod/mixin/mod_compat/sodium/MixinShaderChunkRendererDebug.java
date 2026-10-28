package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import java.util.HashMap;
import java.util.Map;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

/**
 * Reports which GL program each terrain pass is actually drawn with. Off unless
 * {@code vsk.shader.debug=true}.
 *
 * <p>Answers the question that image comparison can only circle around: is the shaderpack's program —
 * vertex stage included — bound when the fluid cull pass draws? Sodium binds a program per pass in
 * {@link ShaderChunkRenderer#begin}, and Iris replaces that method to substitute its own, so reading
 * {@code GL_CURRENT_PROGRAM} straight after it returns gives the program that will run for that pass's
 * draw calls, with no inference involved.</p>
 *
 * <p>The cull pass carrying the same program id as the stock translucent pass is the result to look
 * for; they are the same world fluid and should be running the same shader. A different id, or the
 * plain Sodium {@code ChunkShaderInterface} rather than Iris's, means the pack is not being applied
 * to it.</p>
 *
 * <p>Only the GL program id is reported: it is the one fact that settles the question, and reading it
 * needs nothing from the target class.</p>
 *
 * <p>Logs only when a pass's program changes, so it produces a short table rather than one line per
 * frame.</p>
 */
@Mixin(DefaultChunkRenderer.class)
public class MixinShaderChunkRendererDebug {

    private static final boolean VS$DEBUG =
        Boolean.parseBoolean(System.getProperty("vsk.shader.debug", "false"));
    private static final Logger VS$LOGGER = LogManager.getLogger("VS ShaderDebug");
    private static final Map<String, String> VS$LAST = new HashMap<>();

    /**
     * Sampled here rather than at the end of {@code begin}, because Iris injects into {@code begin} and
     * cancels it to substitute its own program — a TAIL injection there never runs under a shaderpack,
     * which is exactly the case being investigated. By this point {@code begin} has returned and the
     * program for this pass is bound.
     */
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;"
                + "setModelViewMatrix(Lorg/joml/Matrix4fc;)V",
            shift = At.Shift.AFTER
        ),
        remap = false,
        require = 0
    )
    private void vs$reportBoundProgram(final ChunkRenderMatrices matrices, final CommandList commandList,
        final ChunkRenderListIterable renderLists, final TerrainRenderPass pass, final CameraTransform camera,
        final CallbackInfo ci) {
        if (!VS$DEBUG) {
            return;
        }
        final String name;
        if (pass == SodiumCompat.AIR_POCKET_PASS) {
            name = "AIR_CULL   ";
        } else if (pass == DefaultTerrainRenderPasses.TRANSLUCENT) {
            name = "TRANSLUCENT";
        } else if (pass == DefaultTerrainRenderPasses.SOLID) {
            name = "SOLID      ";
        } else if (pass == DefaultTerrainRenderPasses.CUTOUT) {
            name = "CUTOUT     ";
        } else {
            name = String.valueOf(pass);
        }

        final String line = "glProgram=" + GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (!line.equals(VS$LAST.put(name, line))) {
            VS$LOGGER.info("[shaderdebug] pass={} {}", name, line);
        }
    }
}
