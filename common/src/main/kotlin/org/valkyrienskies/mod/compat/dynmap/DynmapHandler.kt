package org.valkyrienskies.mod.compat.dynmap

import net.minecraft.server.level.ServerLevel
import org.dynmap.DynmapCommonAPI
import org.dynmap.DynmapCommonAPIListener
import org.dynmap.markers.GenericMarker
import org.dynmap.markers.Marker
import org.dynmap.markers.MarkerIcon
import org.dynmap.markers.MarkerSet
import org.dynmap.markers.PolyLineMarker
import org.joml.Vector3d
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld
import kotlin.random.Random

abstract class DynmapHandler : DynmapCommonAPIListener() {
    var api: DynmapCommonAPI? = null

    override fun apiEnabled(dynmapCommonAPI: DynmapCommonAPI) {
        this.api = dynmapCommonAPI
    }

    open fun register() {
        register(this)
    }

    fun updateMarkers(level: ServerLevel) {
        val worldName = getWorldName(level)
        val allShips = level.shipObjectWorld.allShips

        val markerSet = getOrCreateMarkerSet() ?: return

        clearUnusedIconMarkers(markerSet, allShips)
        if (VSGameConfig.SERVER.Dynmap.showIconMarkers)
            allShips.forEach { ship -> renderShipIconMarker(ship, markerSet, worldName) }

        clearUnusedPolylineMarkers(markerSet, allShips)
        if (VSGameConfig.SERVER.Dynmap.showPolylineMarkers)
            allShips.forEach { ship -> renderShipPolylineMarker(ship, markerSet, worldName) }
    }

    private fun clearUnusedIconMarkers(markerSet: MarkerSet, data: QueryableShipData<ServerShip>) {
        markerSet.markers?.forEach { marker -> clearUnusedMarker(marker, data, VSGameConfig.SERVER.Dynmap.showIconMarkers) }
    }

    private fun clearUnusedPolylineMarkers(markerSet: MarkerSet, data: QueryableShipData<ServerShip>) {
        markerSet.polyLineMarkers?.forEach { marker -> clearUnusedMarker(marker, data, VSGameConfig.SERVER.Dynmap.showPolylineMarkers) }
    }

    private fun clearUnusedMarker(marker: GenericMarker, data: QueryableShipData<ServerShip>, enabled: Boolean) {
        val id = marker.markerID.replace("ship", "").toLong()
        if (data.getById(id) == null || !enabled)
            marker.deleteMarker()
    }

    private fun renderShipIconMarker(data: ServerShip, markerSet: MarkerSet, world: String) {
        val pos = data.transform.positionInWorld
        val label = createShipLabel(data)
        val icon = getOrCreateIcon()

        val marker: Marker = markerSet.findMarker("ship${data.id}") ?: run {
            markerSet.createMarker("ship${data.id}", label, true, world, pos.x(), pos.y(), pos.z(), icon, true)
            return
        }
        marker.description = label
        marker.setLocation(world, pos.x(), pos.y(), pos.z())
        marker.markerIcon = icon
    }

    private fun renderShipPolylineMarker(data: ServerShip, markerSet: MarkerSet, world: String) {
        val arrays = getArraysFromAABB(data.worldAABB)
        val label = createShipLabel(data)
        val marker: PolyLineMarker = markerSet.findPolyLineMarker("ship${data.id}") ?: run {
            val self = markerSet.createPolyLineMarker("ship${data.id}", label, true, world, arrays.first, arrays.second, arrays.third, true)
            self?.setLineStyle(5, self.lineOpacity, Random.nextInt(0x000000, 0xFFFFFF))
            self ?: return
        }
        marker.description = label
        marker.setCornerLocations(arrays.first, arrays.second, arrays.third)
    }

    private fun getOrCreateMarkerSet(): MarkerSet? =
        this.api?.markerAPI?.getMarkerSet(ValkyrienSkiesMod.MOD_ID) ?: run {
            this.api?.markerAPI?.createMarkerSet(ValkyrienSkiesMod.MOD_ID, "VS Ship Markers", null, true)
        }

    private fun getArraysFromAABB(aabb: AABBdc): Triple<DoubleArray, DoubleArray, DoubleArray> {
        val x = doubleArrayOf(
            aabb.minX(), // 1
            aabb.minX(), // 1
            aabb.minX(), // 1
            aabb.minX(), // 1
            aabb.minX(), // 1
            aabb.minX(), // 2
            aabb.maxX(), // 2
            aabb.maxX(), // 2
            aabb.minX(), // 2
            aabb.maxX(), // 2
            aabb.maxX(), // 3
            aabb.maxX(), // 3
            aabb.maxX(), // 3
            aabb.maxX(), // 3
            aabb.minX(), // 4
            aabb.minX(), // 4
            aabb.maxX() // 4
        )
        val y = doubleArrayOf(
            aabb.minY(), // 1
            aabb.maxY(), // 1
            aabb.maxY(), // 1
            aabb.minY(), // 1
            aabb.minY(), // 1
            aabb.maxY(), // 2
            aabb.maxY(), // 2
            aabb.minY(), // 2
            aabb.minY(), // 2
            aabb.minY(), // 2
            aabb.minY(), // 3
            aabb.maxY(), // 3
            aabb.maxY(), // 3
            aabb.maxY(), // 3
            aabb.maxY(), // 4
            aabb.minY(), // 4
            aabb.minY() // 4
        )
        val z = doubleArrayOf(
            aabb.minZ(), // 1
            aabb.minZ(), // 1
            aabb.maxZ(), // 1
            aabb.maxZ(), // 1
            aabb.minZ(), // 1
            aabb.minZ(), // 2
            aabb.minZ(), // 2
            aabb.minZ(), // 2
            aabb.minZ(), // 2
            aabb.minZ(), // 2
            aabb.maxZ(), // 3
            aabb.maxZ(), // 3
            aabb.minZ(), // 3
            aabb.maxZ(), // 3
            aabb.maxZ(), // 4
            aabb.maxZ(), // 4
            aabb.maxZ() // 4
        )

        return Triple(x, y, z)
    }

    private fun createShipLabel(ship: ServerShip): String {
        var label = "<h1>${ship.slug}</h1>"

        if (VSGameConfig.SERVER.Dynmap.showShipId) label += "<p><strong>Ship ID: </strong>${ship.id}</p>"
        if (VSGameConfig.SERVER.Dynmap.showShipMass) label += "<p><strong>Ship Mass: </strong>${ship.inertiaData.mass} kg</p>"

        return label
    }

    abstract fun getWorldName(level: ServerLevel): String

    abstract fun getOrCreateIcon(): MarkerIcon?
}
