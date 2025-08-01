package org.valkyrienskies.mod.compat;

import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;

public class EmbeddiumCompat {

    public static void onChunkAdded(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

    public static void onChunkRemoved(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }

}
