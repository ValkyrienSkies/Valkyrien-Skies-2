package org.valkyrienskies.mod.forge.integrations.cc_tweaked;

import dan200.computercraft.api.ComputerCraftAPI;

public class ValkyrienSkiesPeripheralProviders {
    public static void registerPeripheralProviders() {
        ComputerCraftAPI.registerPeripheralProvider(new ShipPeripheralProvider());
    }
}
