package org.valkyrienskies.mod.util;

import net.minecraft.client.Minecraft;

/**
 * A helper class to prevent minecraft client only classes be loaded in common mixin
 */
public final class MinecraftClientHelper {
    private MinecraftClientHelper() {}

    public static boolean isSinglePlayerPaused() {
        return Minecraft.getInstance().isPaused();
    }
}
