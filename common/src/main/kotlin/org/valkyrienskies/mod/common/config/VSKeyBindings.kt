package org.valkyrienskies.mod.common.config

import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer
import java.util.function.Supplier

object VSKeyBindings {
    // TODO when making the addon utils for registering... this too
    private val toBeRegistered = mutableListOf<Consumer<Consumer<KeyMapping>>>()

    // val shipUp = register("key.valkyrienskies.ship_up", 32, "category.valkyrienskies.driving")
    val shipDown = register("key.valkyrienskies.ship_down", GLFW.GLFW_KEY_C, "category.valkyrienskies.driving")
    // val shipForward = register("key.valkyrienskies.ship_forward", 87, "category.valkyrienskies.driving")
    // val shipBack = register("key.valkyrienskies.ship_back", 83, "category.valkyrienskies.driving")
    // val shipLeft = register("key.valkyrienskies.ship_left", 65, "category.valkyrienskies.driving")
    // val shipRight = register("key.valkyrienskies.ship_right", 68, "category.valkyrienskies.driving")

    private fun register(name: String, keyCode: Int, category: String): Supplier<KeyMapping> =
        object : Supplier<KeyMapping>, Consumer<Consumer<KeyMapping>> {
            lateinit var registered: KeyMapping

            // If this throws error ur on server
            override fun get(): KeyMapping = registered
            override fun accept(t: Consumer<KeyMapping>) {
                registered = KeyMapping(name, keyCode, category)
                t.accept(registered)
            }
        }.apply { toBeRegistered.add(this) }

    fun clientSetup(registerar: Consumer<KeyMapping>) {
        toBeRegistered.forEach { it.accept(registerar) }
    }
}
