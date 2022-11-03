package org.valkyrienskies.mod.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;

public class RegistryEvents {

    private static List<Runnable> onTagsLoaded = new ArrayList<>();
    private static List<Runnable> onRegistriesComplete = new ArrayList<>();

    // this can be beter...
    public static void onTagsLoaded(final Runnable event) {
        onTagsLoaded.add(event);
    }

    public static void tagsAreLoaded(final RegistryAccess registries, final boolean client) {
        onTagsLoaded.forEach(Runnable::run);
    }

    public static void onRegistriesComplete(final Runnable event) {
        onRegistriesComplete.add(event);
    }

    public static void registriesAreComplete() {
        onRegistriesComplete.forEach(Runnable::run);
    }

}
