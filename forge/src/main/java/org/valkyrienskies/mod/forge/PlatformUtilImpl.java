package org.valkyrienskies.mod.forge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resource.ResourceReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlatformUtilImpl {
    private static List<ResourceReloadListener> listeners = new ArrayList<>();

    public static void registerDataResourceManager(final ResourceReloadListener listener, final String name) {
        listeners.add(listener);
    }

    @SubscribeEvent
    public static void registerResourceManagers(final AddReloadListenerEvent event) {
        listeners.forEach(event::addListener);
        listeners.clear();
    }
}
