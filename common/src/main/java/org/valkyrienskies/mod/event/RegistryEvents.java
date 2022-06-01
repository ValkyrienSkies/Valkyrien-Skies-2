package org.valkyrienskies.mod.event;

import java.util.ArrayList;
import java.util.List;

public class RegistryEvents {

    private static List<Runnable> onTagsLoaded = new ArrayList<>();

    // this can be beter...
    public static void onTagsLoaded(final Runnable event) {
        onTagsLoaded.add(event);
    }

    public static void tagsAreLoaded() {
        onTagsLoaded.forEach(Runnable::run);
    }

}
