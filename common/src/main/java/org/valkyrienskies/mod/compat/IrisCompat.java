package org.valkyrienskies.mod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.ChunkRenderBackendExt;

public class IrisCompat {

    /**
     * We need to call a different begin method for sodium when Iris shaders are loaded
     */
    public static void tryIrisBegin(final ChunkRenderBackend<?> backend, final PoseStack poseStack,
        final BlockRenderPass pass) {

        if (LoadedMods.getIris() && backend instanceof ChunkRenderBackendExt) {
            ((ChunkRenderBackendExt) backend).iris$begin(poseStack, pass);
        } else {
            backend.begin(poseStack);
        }
    }
}
