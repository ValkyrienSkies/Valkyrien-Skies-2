package org.valkyrienskies.mod.compat;

import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;

/**
 * Sodium requires an options storage, but we don't actually use it (we set config values instead)
 * So we use this as a dummy.
 */
public class SodiumDummyOptionsStorage implements OptionStorage<String> {

    @Override
    public String getData() {
        return "";
    }

    @Override
    public void save() {

    }
}
