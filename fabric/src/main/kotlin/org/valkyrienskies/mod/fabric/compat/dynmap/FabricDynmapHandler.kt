package org.valkyrienskies.mod.fabric.compat.dynmap

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import org.dynmap.fabric_1_20.DynmapMod.plugin
import org.dynmap.fabric_1_20.FabricWorld
import org.dynmap.markers.MarkerIcon
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.compat.dynmap.DynmapHandler

class FabricDynmapHandler: DynmapHandler() {
    override fun register() {
        super.register()
        ServerTickEvents.END_WORLD_TICK.register(this::updateMarkers)
    }

    override fun getWorldName(level: ServerLevel): String {
        return FabricWorld.getWorldName(plugin, level)
    }

    override fun getOrCreateIcon(): MarkerIcon? =
        this.api?.markerAPI?.getMarkerIcon("ship") ?: run {
            plugin?.fabricServer?.let {
                this.api?.markerAPI?.createMarkerIcon("ship", "ship",
                    it.openResource(ValkyrienSkiesMod.MOD_ID, "assets/valkyrienskies/icon.png"))
            }
        }
}
