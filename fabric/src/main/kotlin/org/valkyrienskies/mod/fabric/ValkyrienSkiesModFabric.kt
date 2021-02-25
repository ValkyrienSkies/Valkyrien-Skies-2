package org.valkyrienskies.mod.fabric

import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import net.fabricmc.api.ModInitializer

class ValkyrienSkiesModFabric : ModInitializer {
	override fun onInitialize() {
		ValkyrienSkiesMod.init()
		VSFabricNetworking.registerFabricNetworking()
	}
}