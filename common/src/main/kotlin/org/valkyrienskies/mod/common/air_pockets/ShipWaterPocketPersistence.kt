package org.valkyrienskies.mod.common.air_pockets

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.saveddata.SavedData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Double
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.BitSet
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.collections.iterator

internal data class PersistedShipPocketState(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val open: BitSet,
    val exterior: BitSet,
    val strictInterior: BitSet,
    val simulationDomain: BitSet,
    val outsideVoid: BitSet,
    val interior: BitSet,
    val floodFluid: Fluid,
    val flooded: BitSet,
    val materializedWater: BitSet,
    val waterReachable: BitSet,
    val unreachableVoid: BitSet,
    val faceCondXP: ShortArray,
    val faceCondYP: ShortArray,
    val faceCondZP: ShortArray,
    val voxelExteriorComponentMask: LongArray,
    val voxelInteriorComponentMask: LongArray,
    val voxelSimulationComponentMask: LongArray,
    val floodPlaneByComponent: Int2DoubleOpenHashMap,
    val geometryRevision: Long,
    val geometrySignature: Long,
    val requiresResave: Boolean = false,
)

internal class ShipPocketSavedData : SavedData() {
    private val persistedStates: MutableMap<Long, PersistedShipPocketState> = HashMap()

    fun getState(shipId: Long): PersistedShipPocketState? = persistedStates[shipId]

    fun putState(shipId: Long, state: PersistedShipPocketState) {
        persistedStates[shipId] = state
        setDirty()
    }

    fun removeState(shipId: Long) {
        if (persistedStates.remove(shipId) != null) {
            setDirty()
        }
    }

    override fun save(compoundTag: CompoundTag): CompoundTag {
        compoundTag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION)
        val ships = ListTag()
        for ((shipId, state) in persistedStates) {
            val shipTag = CompoundTag()
            shipTag.putLong(TAG_SHIP_ID, shipId)
            shipTag.putInt(TAG_MIN_X, state.minX)
            shipTag.putInt(TAG_MIN_Y, state.minY)
            shipTag.putInt(TAG_MIN_Z, state.minZ)
            shipTag.putInt(TAG_SIZE_X, state.sizeX)
            shipTag.putInt(TAG_SIZE_Y, state.sizeY)
            shipTag.putInt(TAG_SIZE_Z, state.sizeZ)
            shipTag.putString(TAG_FLOOD_FLUID, fluidRegistryName(state.floodFluid))

            shipTag.putLong(TAG_GEOMETRY_REVISION, state.geometryRevision)
            shipTag.putLong(TAG_GEOMETRY_SIGNATURE, state.geometrySignature)

            shipTag.putByteArray(TAG_OPEN, encodeBitSet(state.open))
            shipTag.putByteArray(TAG_EXTERIOR, encodeBitSet(state.exterior))
            shipTag.putByteArray(TAG_STRICT_INTERIOR, encodeBitSet(state.strictInterior))
            shipTag.putByteArray(TAG_SIMULATION_DOMAIN, encodeBitSet(state.simulationDomain))
            shipTag.putByteArray(TAG_OUTSIDE_VOID, encodeBitSet(state.outsideVoid))
            shipTag.putByteArray(TAG_INTERIOR, encodeBitSet(state.interior))
            shipTag.putByteArray(TAG_FLOODED, encodeBitSet(state.flooded))
            shipTag.putByteArray(TAG_MATERIALIZED_WATER, encodeBitSet(state.materializedWater))
            shipTag.putByteArray(TAG_WATER_REACHABLE, encodeBitSet(state.waterReachable))
            shipTag.putByteArray(TAG_UNREACHABLE_VOID, encodeBitSet(state.unreachableVoid))

            shipTag.putByteArray(TAG_FACE_COND_XP, encodeShortArray(state.faceCondXP))
            shipTag.putByteArray(TAG_FACE_COND_YP, encodeShortArray(state.faceCondYP))
            shipTag.putByteArray(TAG_FACE_COND_ZP, encodeShortArray(state.faceCondZP))
            shipTag.putByteArray(TAG_VOXEL_EXTERIOR_COMPONENT_MASK, encodeLongArray(state.voxelExteriorComponentMask))
            shipTag.putByteArray(TAG_VOXEL_INTERIOR_COMPONENT_MASK, encodeLongArray(state.voxelInteriorComponentMask))
            shipTag.putByteArray(TAG_VOXEL_SIMULATION_COMPONENT_MASK, encodeLongArray(state.voxelSimulationComponentMask))

            val planeCount = state.floodPlaneByComponent.size
            val planeKeys = IntArray(planeCount)
            val planeValues = LongArray(planeCount)
            var i = 0
            val it = state.floodPlaneByComponent.int2DoubleEntrySet().iterator()
            while (it.hasNext()) {
                val entry = it.next()
                planeKeys[i] = entry.intKey
                planeValues[i] = Double.doubleToLongBits(entry.doubleValue)
                i++
            }
            shipTag.putIntArray(TAG_FLOOD_PLANE_KEYS, planeKeys)
            shipTag.putLongArray(TAG_FLOOD_PLANE_VALUES, planeValues)

            ships.add(shipTag)
        }
        compoundTag.put(TAG_SHIPS, ships)
        return compoundTag
    }

    companion object {
        private const val FORMAT_VERSION = 3
        private const val TAG_FORMAT_VERSION = "format_version"
        private const val TAG_SHIPS = "ships"
        private const val TAG_SHIP_ID = "ship_id"
        private const val TAG_MIN_X = "min_x"
        private const val TAG_MIN_Y = "min_y"
        private const val TAG_MIN_Z = "min_z"
        private const val TAG_SIZE_X = "size_x"
        private const val TAG_SIZE_Y = "size_y"
        private const val TAG_SIZE_Z = "size_z"
        private const val TAG_FLOOD_FLUID = "flood_fluid"
        private const val TAG_GEOMETRY_REVISION = "geometry_revision"
        private const val TAG_GEOMETRY_SIGNATURE = "geometry_signature"
        private const val TAG_OPEN = "open"
        private const val TAG_EXTERIOR = "exterior"
        private const val TAG_STRICT_INTERIOR = "strict_interior"
        private const val TAG_SIMULATION_DOMAIN = "simulation_domain"
        private const val TAG_OUTSIDE_VOID = "outside_void"
        private const val TAG_INTERIOR = "interior"
        private const val TAG_FLOODED = "flooded"
        private const val TAG_MATERIALIZED_WATER = "materialized_water"
        private const val TAG_WATER_REACHABLE = "water_reachable"
        private const val TAG_UNREACHABLE_VOID = "unreachable_void"
        private const val TAG_FACE_COND_XP = "face_cond_xp"
        private const val TAG_FACE_COND_YP = "face_cond_yp"
        private const val TAG_FACE_COND_ZP = "face_cond_zp"
        private const val TAG_VOXEL_EXTERIOR_COMPONENT_MASK = "voxel_exterior_component_mask"
        private const val TAG_VOXEL_INTERIOR_COMPONENT_MASK = "voxel_interior_component_mask"
        private const val TAG_VOXEL_SIMULATION_COMPONENT_MASK = "voxel_simulation_component_mask"
        private const val TAG_FLOOD_PLANE_KEYS = "flood_plane_keys"
        private const val TAG_FLOOD_PLANE_VALUES = "flood_plane_values"

        fun createEmpty(): ShipPocketSavedData = ShipPocketSavedData()

        @JvmStatic
        fun load(compoundTag: CompoundTag): ShipPocketSavedData {
            val data = ShipPocketSavedData()
            val version = compoundTag.getInt(TAG_FORMAT_VERSION)
            if (version <= 0 || version > FORMAT_VERSION) {
                return data
            }

            val ships = compoundTag.getList(TAG_SHIPS, Tag.TAG_COMPOUND.toInt())
            for (i in 0 until ships.size) {
                val shipTag = ships.getCompound(i)
                val shipId = shipTag.getLong(TAG_SHIP_ID)
                val minX = shipTag.getInt(TAG_MIN_X)
                val minY = shipTag.getInt(TAG_MIN_Y)
                val minZ = shipTag.getInt(TAG_MIN_Z)
                val sizeX = shipTag.getInt(TAG_SIZE_X)
                val sizeY = shipTag.getInt(TAG_SIZE_Y)
                val sizeZ = shipTag.getInt(TAG_SIZE_Z)
                if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) continue
                val volumeLong = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()
                if (volumeLong <= 0L || volumeLong > Int.MAX_VALUE.toLong()) continue
                val volume = volumeLong.toInt()

                val floodFluid = parseFluid(shipTag.getString(TAG_FLOOD_FLUID))
                val floodPlanes = Int2DoubleOpenHashMap()
                val planeKeys = shipTag.getIntArray(TAG_FLOOD_PLANE_KEYS)
                val planeValues = shipTag.getLongArray(TAG_FLOOD_PLANE_VALUES)
                val planeCount = minOf(planeKeys.size, planeValues.size)
                for (planeIdx in 0 until planeCount) {
                    floodPlanes.put(planeKeys[planeIdx], Double.longBitsToDouble(planeValues[planeIdx]))
                }

                val open = decodeBitSet(shipTag.getByteArray(TAG_OPEN))
                val exterior = decodeBitSet(shipTag.getByteArray(TAG_EXTERIOR))
                val interiorLegacy = decodeBitSet(shipTag.getByteArray(TAG_INTERIOR))
                val strictInterior =
                    if (version >= 2) decodeBitSet(shipTag.getByteArray(TAG_STRICT_INTERIOR)) else interiorLegacy.clone() as BitSet
                val simulationDomain =
                    if (version >= 2) decodeBitSet(shipTag.getByteArray(TAG_SIMULATION_DOMAIN)) else interiorLegacy.clone() as BitSet
                var requiresResave = version < FORMAT_VERSION

                val faceCondXPRaw = decodeShortArray(shipTag.getByteArray(TAG_FACE_COND_XP))
                val faceCondYPRaw = decodeShortArray(shipTag.getByteArray(TAG_FACE_COND_YP))
                val faceCondZPRaw = decodeShortArray(shipTag.getByteArray(TAG_FACE_COND_ZP))
                val faceCondXP = sanitizeFaceConductance(faceCondXPRaw, volume).also {
                    if (it.size != faceCondXPRaw.size || (faceCondXPRaw.isNotEmpty() && it.isEmpty())) {
                        requiresResave = true
                    }
                }
                val faceCondYP = sanitizeFaceConductance(faceCondYPRaw, volume).also {
                    if (it.size != faceCondYPRaw.size || (faceCondYPRaw.isNotEmpty() && it.isEmpty())) {
                        requiresResave = true
                    }
                }
                val faceCondZP = sanitizeFaceConductance(faceCondZPRaw, volume).also {
                    if (it.size != faceCondZPRaw.size || (faceCondZPRaw.isNotEmpty() && it.isEmpty())) {
                        requiresResave = true
                    }
                }

                val voxelExteriorMaskRaw = decodeLongArray(shipTag.getByteArray(TAG_VOXEL_EXTERIOR_COMPONENT_MASK))
                val voxelExteriorMask = sanitizeVoxelMasks(voxelExteriorMaskRaw, volume).also {
                    if (it.size != voxelExteriorMaskRaw.size) requiresResave = true
                }
                val voxelInteriorMaskRaw = decodeLongArray(shipTag.getByteArray(TAG_VOXEL_INTERIOR_COMPONENT_MASK))
                val voxelInteriorMask = sanitizeVoxelMasks(voxelInteriorMaskRaw, volume).also {
                    if (it.size != voxelInteriorMaskRaw.size) requiresResave = true
                }
                val voxelSimulationMaskRaw =
                    if (version >= 2) decodeLongArray(shipTag.getByteArray(TAG_VOXEL_SIMULATION_COMPONENT_MASK))
                    else voxelInteriorMask.copyOf()
                val voxelSimulationMask = sanitizeVoxelMasks(voxelSimulationMaskRaw, volume).also {
                    if (it.size != voxelSimulationMaskRaw.size) requiresResave = true
                }

                val flooded = decodeBitSet(shipTag.getByteArray(TAG_FLOODED))
                val materializedWater = decodeBitSet(shipTag.getByteArray(TAG_MATERIALIZED_WATER))
                val waterReachable = decodeBitSet(shipTag.getByteArray(TAG_WATER_REACHABLE))
                val unreachableVoid = decodeBitSet(shipTag.getByteArray(TAG_UNREACHABLE_VOID))
                val outsideVoid =
                    if (version >= 3) {
                        decodeBitSet(shipTag.getByteArray(TAG_OUTSIDE_VOID))
                    } else {
                        reconstructOutsideVoidForLegacy(
                            open = open,
                            simulationDomain = simulationDomain,
                            sizeX = sizeX,
                            sizeY = sizeY,
                            sizeZ = sizeZ,
                            faceCondXP = faceCondXP,
                            faceCondYP = faceCondYP,
                            faceCondZP = faceCondZP,
                        ).also {
                            requiresResave = true
                        }
                    }

                val normalized = normalizePersistedMasks(
                    volume = volume,
                    open = open,
                    exterior = exterior,
                    strictInterior = strictInterior,
                    simulationDomain = simulationDomain,
                    outsideVoid = outsideVoid,
                    interiorLegacy = interiorLegacy,
                    flooded = flooded,
                    materializedWater = materializedWater,
                    waterReachable = waterReachable,
                    unreachableVoid = unreachableVoid,
                )
                if (normalized) {
                    requiresResave = true
                }

                data.persistedStates[shipId] = PersistedShipPocketState(
                    minX = minX,
                    minY = minY,
                    minZ = minZ,
                    sizeX = sizeX,
                    sizeY = sizeY,
                    sizeZ = sizeZ,
                    open = open,
                    exterior = exterior,
                    strictInterior = strictInterior,
                    simulationDomain = simulationDomain,
                    outsideVoid = outsideVoid,
                    interior = interiorLegacy,
                    floodFluid = floodFluid,
                    flooded = flooded,
                    materializedWater = materializedWater,
                    waterReachable = waterReachable,
                    unreachableVoid = unreachableVoid,
                    faceCondXP = faceCondXP,
                    faceCondYP = faceCondYP,
                    faceCondZP = faceCondZP,
                    voxelExteriorComponentMask = voxelExteriorMask,
                    voxelInteriorComponentMask = voxelInteriorMask,
                    voxelSimulationComponentMask = voxelSimulationMask,
                    floodPlaneByComponent = floodPlanes,
                    geometryRevision = shipTag.getLong(TAG_GEOMETRY_REVISION),
                    geometrySignature = shipTag.getLong(TAG_GEOMETRY_SIGNATURE),
                    requiresResave = requiresResave,
                )
            }
            return data
        }

        private fun fluidRegistryName(fluid: Fluid): String {
            val key = BuiltInRegistries.FLUID.getKey(canonicalFloodSource(fluid))
            return key?.toString() ?: BuiltInRegistries.FLUID.getKey(Fluids.WATER).toString()
        }

        private fun parseFluid(id: String): Fluid {
            val location = ResourceLocation.tryParse(id) ?: return Fluids.WATER
            val fluid = BuiltInRegistries.FLUID.get(location)
            return if (fluid == Fluids.EMPTY) Fluids.WATER else canonicalFloodSource(fluid)
        }
    }
}

internal object ShipWaterPocketPersistence {
    private const val SAVE_KEY = "valkyrienair_ship_pockets_v1"

    fun get(level: ServerLevel): ShipPocketSavedData {
        return level.dataStorage.computeIfAbsent(
            ShipPocketSavedData::load,
            ShipPocketSavedData::createEmpty,
            SAVE_KEY,
        )
    }
}

internal fun snapshotStateForPersistence(state: ShipPocketState): PersistedShipPocketState {
    return PersistedShipPocketState(
        minX = state.minX,
        minY = state.minY,
        minZ = state.minZ,
        sizeX = state.sizeX,
        sizeY = state.sizeY,
        sizeZ = state.sizeZ,
        open = state.open.clone() as BitSet,
        exterior = state.exterior.clone() as BitSet,
        strictInterior = state.strictInterior.clone() as BitSet,
        simulationDomain = state.simulationDomain.clone() as BitSet,
        outsideVoid = state.outsideVoid.clone() as BitSet,
        interior = state.interior.clone() as BitSet,
        floodFluid = canonicalFloodSource(state.floodFluid),
        flooded = state.flooded.clone() as BitSet,
        materializedWater = state.materializedWater.clone() as BitSet,
        waterReachable = state.waterReachable.clone() as BitSet,
        unreachableVoid = state.unreachableVoid.clone() as BitSet,
        faceCondXP = state.faceCondXP.copyOf(),
        faceCondYP = state.faceCondYP.copyOf(),
        faceCondZP = state.faceCondZP.copyOf(),
        voxelExteriorComponentMask = state.voxelExteriorComponentMask.copyOf(),
        voxelInteriorComponentMask = state.voxelInteriorComponentMask.copyOf(),
        voxelSimulationComponentMask = state.voxelSimulationComponentMask.copyOf(),
        floodPlaneByComponent = Int2DoubleOpenHashMap(state.floodPlaneByComponent),
        geometryRevision = state.geometryRevision,
        geometrySignature = state.geometrySignature,
    )
}

internal fun applyPersistedState(state: ShipPocketState, persisted: PersistedShipPocketState) {
    state.minX = persisted.minX
    state.minY = persisted.minY
    state.minZ = persisted.minZ
    state.sizeX = persisted.sizeX
    state.sizeY = persisted.sizeY
    state.sizeZ = persisted.sizeZ
    state.open = persisted.open.clone() as BitSet
    state.exterior = persisted.exterior.clone() as BitSet
    state.strictInterior = persisted.strictInterior.clone() as BitSet
    state.simulationDomain = persisted.simulationDomain.clone() as BitSet
    state.outsideVoid = persisted.outsideVoid.clone() as BitSet
    state.interior = persisted.strictInterior.clone() as BitSet
    state.floodFluid = canonicalFloodSource(persisted.floodFluid)
    state.flooded = persisted.flooded.clone() as BitSet
    state.materializedWater = persisted.materializedWater.clone() as BitSet
    state.waterReachable = persisted.waterReachable.clone() as BitSet
    state.unreachableVoid = persisted.unreachableVoid.clone() as BitSet
    state.faceCondXP = persisted.faceCondXP.copyOf()
    state.faceCondYP = persisted.faceCondYP.copyOf()
    state.faceCondZP = persisted.faceCondZP.copyOf()
    state.shapeTemplatePalette = emptyList()
    state.templateIndexByVoxel = IntArray(0)
    state.voxelExteriorComponentMask = persisted.voxelExteriorComponentMask.copyOf()
    state.voxelInteriorComponentMask = persisted.voxelInteriorComponentMask.copyOf()
    state.voxelSimulationComponentMask = persisted.voxelSimulationComponentMask.copyOf()
    state.componentGraphDegraded = true
    state.floodPlaneByComponent = Int2DoubleOpenHashMap(persisted.floodPlaneByComponent)
    state.geometryRevision = persisted.geometryRevision
    state.geometrySignature = persisted.geometrySignature
    state.dirty = true
    state.persistDirty = persisted.requiresResave
    state.restoredFromPersistence = true
    state.awaitingGeometryValidation = true
}

private fun sanitizeFaceConductance(values: ShortArray, volume: Int): ShortArray {
    if (values.isEmpty()) return values
    return if (values.size >= volume) values.copyOf(volume) else ShortArray(0)
}

private fun sanitizeVoxelMasks(values: LongArray, volume: Int): LongArray {
    return if (values.size > volume) values.copyOf(volume) else values
}

private fun reconstructOutsideVoidForLegacy(
    open: BitSet,
    simulationDomain: BitSet,
    sizeX: Int,
    sizeY: Int,
    sizeZ: Int,
    faceCondXP: ShortArray,
    faceCondYP: ShortArray,
    faceCondZP: ShortArray,
): BitSet {
    val volume = sizeX * sizeY * sizeZ
    val hasValidFaceConductance =
        faceCondXP.size == volume &&
            faceCondYP.size == volume &&
            faceCondZP.size == volume
    if (hasValidFaceConductance) {
        return computeOutsideVoidFromGeometry(
            open = open,
            simulationDomain = simulationDomain,
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            faceCondXP = faceCondXP,
            faceCondYP = faceCondYP,
            faceCondZP = faceCondZP,
        )
    }
    val fallback = open.clone() as BitSet
    fallback.andNot(simulationDomain)
    return fallback
}

private fun normalizePersistedMasks(
    volume: Int,
    open: BitSet,
    exterior: BitSet,
    strictInterior: BitSet,
    simulationDomain: BitSet,
    outsideVoid: BitSet,
    interiorLegacy: BitSet,
    flooded: BitSet,
    materializedWater: BitSet,
    waterReachable: BitSet,
    unreachableVoid: BitSet,
): Boolean {
    var changed = false

    fun clampToVolume(bits: BitSet): Boolean {
        val firstOutOfRange = bits.nextSetBit(volume)
        if (firstOutOfRange >= 0) {
            bits.clear(volume, bits.length())
            return true
        }
        return false
    }

    fun clampSubset(bits: BitSet, superset: BitSet): Boolean {
        val originalCardinality = bits.cardinality()
        bits.and(superset)
        return bits.cardinality() != originalCardinality
    }

    changed = clampToVolume(open) || changed
    changed = clampToVolume(exterior) || changed
    changed = clampToVolume(strictInterior) || changed
    changed = clampToVolume(simulationDomain) || changed
    changed = clampToVolume(outsideVoid) || changed
    changed = clampToVolume(interiorLegacy) || changed
    changed = clampToVolume(flooded) || changed
    changed = clampToVolume(materializedWater) || changed
    changed = clampToVolume(waterReachable) || changed
    changed = clampToVolume(unreachableVoid) || changed

    changed = clampSubset(exterior, open) || changed
    changed = clampSubset(strictInterior, open) || changed
    changed = clampSubset(simulationDomain, open) || changed
    changed = clampSubset(interiorLegacy, open) || changed
    changed = clampSubset(flooded, open) || changed
    changed = clampSubset(materializedWater, open) || changed
    changed = clampSubset(waterReachable, open) || changed
    changed = clampSubset(unreachableVoid, open) || changed

    changed = clampSubset(flooded, simulationDomain) || changed
    changed = clampSubset(materializedWater, simulationDomain) || changed

    changed = clampSubset(outsideVoid, open) || changed
    val outsideBefore = outsideVoid.cardinality()
    outsideVoid.andNot(simulationDomain)
    if (outsideVoid.cardinality() != outsideBefore) {
        changed = true
    }

    return changed
}

private fun canonicalFloodSource(fluid: Fluid): Fluid {
    return if (fluid is FlowingFluid) fluid.source else fluid
}

private fun encodeBitSet(bits: BitSet): ByteArray = encodeLongArray(bits.toLongArray())

private fun decodeBitSet(bytes: ByteArray): BitSet {
    val longs = decodeLongArray(bytes)
    return BitSet.valueOf(longs)
}

private fun encodeShortArray(values: ShortArray): ByteArray {
    if (values.isEmpty()) return ByteArray(0)
    val raw = ByteArray(values.size * 2)
    val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    for (value in values) {
        bb.putShort(value)
    }
    return compressBytes(raw)
}

private fun decodeShortArray(bytes: ByteArray): ShortArray {
    if (bytes.isEmpty()) return ShortArray(0)
    val raw = decompressBytes(bytes)
    if (raw.isEmpty() || raw.size % 2 != 0) return ShortArray(0)
    val out = ShortArray(raw.size / 2)
    val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    for (i in out.indices) {
        out[i] = bb.getShort()
    }
    return out
}

private fun encodeLongArray(values: LongArray): ByteArray {
    if (values.isEmpty()) return ByteArray(0)
    val raw = ByteArray(values.size * 8)
    val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    for (value in values) {
        bb.putLong(value)
    }
    return compressBytes(raw)
}

private fun decodeLongArray(bytes: ByteArray): LongArray {
    if (bytes.isEmpty()) return LongArray(0)
    val raw = decompressBytes(bytes)
    if (raw.isEmpty() || raw.size % 8 != 0) return LongArray(0)
    val out = LongArray(raw.size / 8)
    val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    for (i in out.indices) {
        out[i] = bb.getLong()
    }
    return out
}

private fun compressBytes(raw: ByteArray): ByteArray {
    if (raw.isEmpty()) return raw
    val output = ByteArrayOutputStream(raw.size)
    DeflaterOutputStream(output).use { deflater ->
        deflater.write(raw)
    }
    return output.toByteArray()
}

private fun decompressBytes(compressed: ByteArray): ByteArray {
    if (compressed.isEmpty()) return compressed
    return try {
        InflaterInputStream(ByteArrayInputStream(compressed)).use { inflater ->
            inflater.readBytes()
        }
    } catch (_: Throwable) {
        ByteArray(0)
    }
}
