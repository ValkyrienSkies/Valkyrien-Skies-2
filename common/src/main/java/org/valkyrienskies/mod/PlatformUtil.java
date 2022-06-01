package org.valkyrienskies.mod;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.resource.ResourceReloadListener;

public class PlatformUtil {

    @ExpectPlatform
    public static void registerDataResourceManager(final ResourceReloadListener listener, final String name) {
        throw new AssertionError();
    }
}
