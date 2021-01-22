package net.examplemod.forge

import net.examplemod.ExampleMod
import net.minecraftforge.fml.common.Mod

@Mod(ExampleMod.MOD_ID)
class ExampleModForge {
	init {
		ExampleMod.init()
	}
}