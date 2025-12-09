package org.valkyrienskies.mod.forge.compat.dynmap

import net.minecraft.server.level.ServerLevel
import net.minecraftforge.event.TickEvent.LevelTickEvent
import org.dynmap.forge_1_20.DynmapMod.plugin
import org.dynmap.forge_1_20.ForgeWorld
import org.dynmap.markers.MarkerIcon
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.compat.dynmap.DynmapHandler

class ForgeDynmapHandler : DynmapHandler() {
    override fun register() {
        super.register()
        INSTANCE = this
    }

    override fun getWorldName(level: ServerLevel): String {
        return ForgeWorld.getWorldName(level)
    }

    override fun getOrCreateIcon(): MarkerIcon? =
        this.api?.markerAPI?.getMarkerIcon("ship") ?: run {
            plugin?.ForgeServer()?.let {
                this.api?.markerAPI?.createMarkerIcon("ship", "ship",
                    it.openResource(ValkyrienSkiesMod.MOD_ID, "assets/valkyrienskies/icon.png"))
            }
        }

    companion object {
        var INSTANCE: ForgeDynmapHandler? = null

        fun tick(event: LevelTickEvent) {
            (event.level as? ServerLevel)?.let {
                INSTANCE?.updateMarkers(it)
            }
        }
    }
}
