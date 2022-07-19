package org.valkyrienskies.mod.common.config

import net.minecraft.client.KeyMapping
import java.util.function.Consumer
import java.util.function.Supplier

object VSKeyBindings {
    val shipUp = register("key.valkyrienskies.ship_up", 32, "category.valkyrienskies.driving")
    val shipDown = register("key.valkyrienskies.ship_down", 81, "category.valkyrienskies.driving")
    val shipForward = register("key.valkyrienskies.ship_forward", 87, "category.valkyrienskies.driving")
    val shipBack = register("key.valkyrienskies.ship_back", 83, "category.valkyrienskies.driving")
    val shipLeft = register("key.valkyrienskies.ship_left", 65, "category.valkyrienskies.driving")
    val shipRight = register("key.valkyrienskies.ship_right", 68, "category.valkyrienskies.driving")

    private var registerar = Consumer<KeyMapping> { throw RuntimeException("Keybinds don't exist on servers") }
    private fun register(name: String, keyCode: Int, category: String): Supplier<KeyMapping> =
        object : Supplier<KeyMapping> {
            lateinit var cached: KeyMapping

            override fun get(): KeyMapping = if (::cached.isInitialized) cached else
                KeyMapping(name, keyCode, category).apply { registerar.accept(this); cached = this }
        }

    fun clientSetup(registerar: Consumer<KeyMapping>) {
        this.registerar = registerar
    }
}
