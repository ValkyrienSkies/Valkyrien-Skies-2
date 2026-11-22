package org.valkyrienskies.mod.gametest.client

import com.simibubi.create.content.trains.entity.TrainRelocator
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

// Fabric side of the shared client tests. Run with DISPLAY set: ./gradlew :fabric:runGameTestClient

class FabricClientTestHook : ClientModInitializer {
    override fun onInitializeClient() {
        if (ClientShipTestRunner.enabled) ClientTickEvents.END_CLIENT_TICK.register { ClientShipTestRunner.tick(it) }
    }
}

fun clickTrainRelocator(mc: Minecraft) {
    TrainRelocator.onClicked()
}

fun platformClientTests(): List<ClientShipTest> = emptyList()
