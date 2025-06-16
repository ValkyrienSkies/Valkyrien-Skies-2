package org.valkyrienskies.mod.event;

import kotlin.Unit;
import net.minecraft.core.RegistryAccess;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;

public class RegistryEvents {

    /**
     * @deprecated Use VSGameEvents
     */
    @Deprecated(forRemoval = true)
    public static void onTagsLoaded(final Runnable event) {
        VSGameEvents.INSTANCE.getTagsAreLoaded().on(x -> event.run());
    }

    /**
     * @deprecated Use VSGameEvents
     */
    @Deprecated(forRemoval = true)
    public static void tagsAreLoaded(final RegistryAccess registries, final boolean client) {
        VSGameEvents.INSTANCE.getTagsAreLoaded().emit(Unit.INSTANCE);
    }

    /**
     * @deprecated Use VSGameEvents
     */
    @Deprecated(forRemoval = true)
    public static void onRegistriesComplete(final Runnable event) {
        VSGameEvents.INSTANCE.getRegistriesCompleted().on(x -> event.run());
    }

    /**
     * @deprecated Use VSGameEvents
     */
    @Deprecated(forRemoval = true)
    public static void registriesAreComplete() {
        VSGameEvents.INSTANCE.getRegistriesCompleted().emit(Unit.INSTANCE);
    }

}
