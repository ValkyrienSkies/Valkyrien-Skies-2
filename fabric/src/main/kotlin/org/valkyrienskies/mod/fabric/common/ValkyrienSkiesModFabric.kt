package org.valkyrienskies.mod.fabric.common

import net.fabricmc.api.ModInitializer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

class ValkyrienSkiesModFabric : ModInitializer {
	override fun onInitialize() {
		ValkyrienSkiesMod.init()
		VSFabricNetworking.registerFabricNetworking()
	}
}