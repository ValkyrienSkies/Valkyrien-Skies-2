package org.valkyrienskies.mod.fabric.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.fabric.common.VSFabricNetworking

/**
 * This class only runs on the client, used to initialize client only code. See [ClientModInitializer] for more details.
 */
class ValkyrienSkiesModFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        VSFabricNetworking.registerClientPacketHandlers()

        // Register the ship mounting entity renderer
        EntityRendererRegistry.INSTANCE.register(
            ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE
        ) { manager: EntityRenderDispatcher, _: EntityRendererRegistry.Context ->
            EmptyRenderer(
                manager
            )
        }

        VSKeyBindings.clientSetup {
            KeyBindingHelper.registerKeyBinding(it)
        }
    }
}
