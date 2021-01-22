package net.examplemod.fabric

import net.examplemod.ExampleMod
import net.fabricmc.api.ModInitializer

class ExampleModFabric : ModInitializer {
	override fun onInitialize() {
		ExampleMod.init()
	}
}