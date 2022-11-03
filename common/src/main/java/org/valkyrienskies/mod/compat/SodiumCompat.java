package org.valkyrienskies.mod.compat;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;

public class SodiumCompat {

    public static void onChunkAdded(final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            SodiumWorldRenderer.instance().onChunkAdded(x, z);
        }
    }

    public static void onChunkRemoved(final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            SodiumWorldRenderer.instance().onChunkRemoved(x, z);
        }
    }

}
