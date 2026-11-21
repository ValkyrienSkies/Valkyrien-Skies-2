package org.valkyrienskies.mod.common.portals

import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.bodies.properties.BodyPose
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.internal.world.VsiPhysLevel
import org.valkyrienskies.mod.api.portals.ShipPortalMouth
import org.valkyrienskies.mod.api.portals.ShipPortalPair
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import java.util.concurrent.ConcurrentHashMap

@OptIn(VsBeta::class, PhysTickOnly::class)
internal object ShipPortalRegistry {
    val updates = ShipPortalUpdates()

    fun init() {
        ValkyrienSkiesMod.vsCore.physTickEvent.on { event ->
            val level = event.world as? VsiPhysLevel ?: return@on
            updates.apply(level.dimension, level::syncTerrainPortal)
        }
    }

    @JvmStatic
    fun clear() = updates.clear()
}

@OptIn(VsBeta::class, PhysTickOnly::class)
internal class ShipPortalUpdates {
    private data class Frame(override val position: Vector3d, override val rotation: Quaterniond) : BodyPose
    private data class Placement(val pair: ShipPortalPair, val carrierA: Long?, val carrierB: Long?)
    private class Change(val definition: VsiPhysLevel.TerrainPortalDefinition?, val carrierA: Long? = null, val carrierB: Long? = null)
    private val desired = HashMap<String, MutableMap<ResourceLocation, Placement>>()
    private val pending = ConcurrentHashMap<String, ConcurrentHashMap<String, Change>>()

    fun set(dimension: String, key: ResourceLocation, pair: ShipPortalPair, carrierA: Long? = null, carrierB: Long? = null) {
        val placement = Placement(pair, carrierA, carrierB)
        if (desired.getOrPut(dimension, ::HashMap).put(key, placement) == placement) return
        queue(dimension, key, Change(VsiPhysLevel.TerrainPortalDefinition(
            frame(pair.a), frame(pair.b), pair.halfWidth, pair.halfHeight), carrierA, carrierB))
    }

    fun remove(dimension: String, key: ResourceLocation) {
        desired[dimension]?.remove(key)
        queue(dimension, key, Change(null))
    }

    private fun queue(dimension: String, key: ResourceLocation, change: Change) {
        pending.computeIfAbsent(dimension) { ConcurrentHashMap() }["${key.namespace}/${key.path}"] = change
    }

    fun apply(dimension: String, sync: (String, VsiPhysLevel.TerrainPortalDefinition?, Long?, Long?) -> Boolean) {
        val changes = pending[dimension] ?: return
        for ((key, change) in changes) {
            if (sync(key, change.definition, change.carrierA, change.carrierB)) changes.remove(key, change)
        }
    }

    fun clear() {
        desired.clear()
        pending.clear()
    }

    private fun frame(mouth: ShipPortalMouth): Frame {
        val x = mouth.right
        val y = mouth.up
        val z = x.cross(y)
        val basis = Matrix3d(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z)
        return Frame(Vector3d(mouth.center.x, mouth.center.y, mouth.center.z), Quaterniond().setFromNormalized(basis).normalize())
    }
}
