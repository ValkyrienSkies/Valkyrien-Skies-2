package org.valkyrienskies.mod.fabric.integration.cc_restitched;

import dan200.computercraft.api.ComputerCraftAPI;

public class ValkyrienSkiesPeripheralProviders {
    public static void registerPeripheralProviders() {
        ComputerCraftAPI.registerPeripheralProvider(new ShipPeripheralProvider());
    }
}
