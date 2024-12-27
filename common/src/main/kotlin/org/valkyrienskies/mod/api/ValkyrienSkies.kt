/*
 * This is basically the god class for all functions needed by addon developers.
 *
 * This class may be moved into a separate mod at some point, so that it can
 * be shaded. Therefore, only use standard libraries, Minecraft classes, and
 * classes in
 *
 * - org.valkyrienskies.core.api.*
 * - org.valkyrienskies.mod.api.*
 * - org.joml.*
 *
 * Notably do NOT use classes from
 * - org.valkyrienskies.mod.* (except api)
 * - org.valkyrienskies.core.util.*
 * - org.valkyrienskies.core.apigame.*
 *
 * Style notes:
 *
 * Since this class will also be used from Java, try to make parameters nullable
 * wherever possible. Null-checking is pretty cumbersome in Java, so for each
 * function that takes non-nullable parameters and returns a non-nullable type,
 * make a variant that takes nullable parameters and returns a nullable type as
 * well.
 *
 * Prefer to use extension functions and fields rather than global functions.
 */
@file:JvmName("ValkyrienSkies")
package org.valkyrienskies.mod.api

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.Vec3i
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.jetbrains.annotations.Contract
import org.joml.Matrix3f
import org.joml.Matrix4d
import org.joml.Matrix4dc
import org.joml.Quaterniondc
import org.joml.Quaternionf
import org.joml.Vector2i
import org.joml.Vector2ic
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3f
import org.joml.Vector3i
import org.joml.Vector3ic
import org.joml.Vector4f
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.util.functions.DoubleTernaryConsumer
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.math.sqrt

/**
 * The singleton instance of [VsApi].
 */
@get:JvmName("api")
val vsApi: VsApi by lazy {
    try {
        val modClass = Class.forName("org.valkyrienskies.mod.common.ValkyrienSkiesMod")
        val getApi = modClass.getDeclaredMethod("getApi")
        val instance = getApi.invoke(null) as VsApi

        instance
    } catch (ex: Exception) {
        throw IllegalStateException("Failed initialize the Valkyrien Skies API. " +
            "Suggestion: Ensure that you have Valkyrien Skies installed.", ex)
    }
}

/**
 * The String/[DimensionId] used within vs-core for representing this [Level].
 *
 * This is a Kotlin-only function.
 */
val Level.dimensionId: DimensionId
    @JvmSynthetic
    @JvmName("getDimensionIdNonnull")
    get() = vsApi.getDimensionId(this)

/**
 * The String/[DimensionId] used within vs-core for representing this [Level].
 */
val Level?.dimensionId: DimensionId?
    @Contract("null -> null; !null -> !null")
    get() = this?.dimensionId


/**
 * The [ServerShipWorld] associated with this [MinecraftServer] if it exists.
 */
val MinecraftServer?.shipWorld: ServerShipWorld? get() =
    vsApi.getServerShipWorld(this)

/**
 * The [ClientShipWorld] associated with this [Minecraft] if it exists.
 */
val Minecraft?.shipWorld: ClientShipWorld? get() =
    vsApi.getClientShipWorld(this)

val Level?.shipWorld: ShipWorld? get() =
    vsApi.getShipWorld(this)

fun Level?.getShipById(id: ShipId): Ship? =
    shipWorld?.allShips?.getById(id)

fun Level?.isBlockInShipyard(blockX: Int, blockY: Int, blockZ: Int): Boolean =
    isChunkInShipyard(blockX shr 4, blockZ shr 4)

fun Level?.isChunkInShipyard(chunkX: Int, chunkZ: Int): Boolean =
    vsApi.isChunkInShipyard(this, chunkX, chunkZ)

fun Level?.getShipManagingChunk(chunkX: Int, chunkZ: Int): Ship? =
    vsApi.getShipManagingChunk(this, chunkX, chunkZ)

fun Level?.getShipManagingChunk(pos: ChunkPos?): Ship? =
    pos?.let { getShipManagingChunk(pos.x, pos.z) }

fun Level?.getShipManagingBlock(x: Int, y: Int, z: Int): Ship? =
    getShipManagingChunk(x shr 4, z shr 4)

fun Level?.getShipManagingBlock(pos: BlockPos?): Ship? =
    pos?.let { getShipManagingBlock(pos.x, pos.y, pos.z) }

fun Level?.getShipManagingBlock(x: Double, y: Double, z: Double): Ship? =
    getShipManagingBlock(x.toInt(), y.toInt(), z.toInt())

fun Level?.getShipManagingBlock(v: Vector3dc?): Ship? =
    v?.let { getShipManagingBlock(v.x(), v.y(), v.z()) }

fun Level?.getShipManagingBlock(v: Position?): Ship? =
    v?.let { getShipManagingBlock(v.x(), v.y(), v.z()) }

/**
 * Convenience function for
 * `entity.level().getShipManagingBlock(entity.position())`.
 *
 * @see getShipManagingBlock
 */
fun Entity?.getShipManagingEntity(): Ship? =
    this?.level()?.getShipManagingBlock(position())

/**
 * If both endpoints of the given [aabb] are in the same ship, transform them
 * to the world and return the new AABB. Otherwise, leaves it untouched.
 */
fun Level?.toWorld(aabb: AABBdc, dest: AABBd): AABBd {
    val ship1 = getShipManagingBlock(aabb.minX(), aabb.minY(), aabb.minZ())
        ?: return dest.set(aabb)
    val ship2 = getShipManagingBlock(aabb.maxX(), aabb.maxY(), aabb.maxZ())
        ?: return dest.set(aabb)

    // if both endpoints of the aabb are in the same ship, do the transform
    if (ship1.id == ship2.id) {
        return aabb.transform(ship1.shipToWorld, dest)
    }

    return dest.set(aabb)
}

fun Level?.toWorld(aabb: AABBd) =
    toWorld(aabb, aabb)

fun Level?.toWorld(aabb: AABB): AABB =
    toWorld(aabb.toJOML()).toMinecraft()


fun Level?.positionToWorld(pos: Vec3): Vec3 =
    getShipManagingBlock(pos).positionToWorld(pos)

fun Level?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

fun Level?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    getShipManagingBlock(pos).positionToWorld(pos, dest)

fun Ship?.positionToWorld(pos: Vec3): Vec3 =
    this?.transform.positionToWorld(pos)

fun Ship?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

fun Ship?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.transform.positionToWorld(pos, dest)

fun Ship?.positionToShip(pos: Vec3): Vec3 =
    this?.transform.positionToShip(pos)

fun Ship?.positionToShip(pos: Vector3d): Vector3d =
    positionToShip(pos, pos)

fun Ship?.positionToShip(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.transform.positionToShip(pos, dest)

fun ShipTransform?.positionToWorld(pos: Vec3): Vec3 =
    this?.shipToWorld?.transformPosition(pos) ?: pos

fun ShipTransform?.positionToWorld(pos: Vector3d): Vector3d =
    positionToWorld(pos, pos)

fun ShipTransform?.positionToWorld(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.shipToWorld?.transformPosition(pos, dest) ?: dest.set(pos)

fun ShipTransform?.positionToShip(pos: Vec3): Vec3 =
    this?.worldToShip?.transformPosition(pos) ?: pos

fun ShipTransform?.positionToShip(pos: Vector3d): Vector3d =
    positionToShip(pos, pos)

fun ShipTransform?.positionToShip(pos: Vector3dc, dest: Vector3d): Vector3d =
    this?.worldToShip?.transformPosition(pos, dest) ?: dest.set(pos)

/**
 * Returns all the ships whose AABBs contain x/y/z
 */
fun Level?.getShipsIntersecting(x: Double, y: Double, z: Double): Iterable<Ship> =
    vsApi.getShipsIntersecting(this, x, y, z)

/**
 * Returns all the ships whose AABBs intersect [aabb]
 */
fun Level?.getShipsIntersecting(aabb: AABBdc?): Iterable<Ship> =
    vsApi.getShipsIntersecting(this, aabb)

/**
 * Transforms the given world position x/y/z into the ship space of all ships whose AABBs contain x/y/z
 */
fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double): Iterable<Vector3d> =
    getShipsIntersecting(x, y, z).map { it.positionToShip(Vector3d(x, y, z)) }

/**
 * Transforms the given world position into the ship space of all ships whose AABB intersects [aabb]
 */
fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabb: AABBdc?): Iterable<Vector3d> {
    if (this == null || aabb == null) return emptyList()
    return getShipsIntersecting(aabb).map { it.positionToShip(Vector3d(x, y, z)) }
}

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabbRadius: Double): Iterable<Vector3d> =
    positionToNearbyShips(x, y, z, newAABBWithRadius(x, y, z, aabbRadius))

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, null, cb::accept)

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabb: AABBdc?, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, aabb, cb::accept)

fun Level?.positionToNearbyShips(x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShips(x, y, z, newAABBWithRadius(x, y, z, aabbRadius), cb)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double): Iterable<Vector3d> =
    listOf(Vector3d(x, y, z)) + positionToNearbyShips(x, y, z)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabb: AABBdc?): Iterable<Vector3d> =
    listOf(Vector3d(x, y, z)) + positionToNearbyShips(x, y, z, aabb)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double): Iterable<Vector3d> =
    positionToNearbyShipsAndWorld(x, y, z, newAABBWithRadius(x, y, z, aabbRadius))

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, null, cb::accept)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabb: AABBdc?, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, aabb, cb::accept)

fun Level?.positionToNearbyShipsAndWorld(x: Double, y: Double, z: Double, aabbRadius: Double, cb: DoubleTernaryConsumer): Unit =
    positionToNearbyShipsAndWorld(x, y, z, newAABBWithRadius(x, y, z, aabbRadius), cb)

private fun newAABBWithRadius(x: Double, y: Double, z: Double, r: Double) =
    AABBd(x - r, y - r, z - r, x + r, y + r, z + r)

private inline fun Level?.positionToNearbyShipsAndWorld(
    x: Double,
    y: Double,
    z: Double,
    aabb: AABBdc?,
    cb: (Double, Double, Double) -> Unit
) {
    cb(x, y, z)
    positionToNearbyShips(x, y, z, aabb, cb)
}

/**
 * Gets all ships intersecting [aabb] (or x, y, z if [aabb] is null), then
 * transforms the position x, y, z to their respective ship spaces
 * and calls [cb] with the transformed positions.
 */
private inline fun Level?.positionToNearbyShips(
    x: Double,
    y: Double,
    z: Double,
    aabb: AABBdc?,
    cb: (Double, Double, Double) -> Unit
) {
    val ships = aabb?.let(this::getShipsIntersecting)
        ?: this.getShipsIntersecting(x, y, z)

    for (ship in ships) {
        ship.worldToShip.transformPositionInline(x, y, z, cb)
    }
}

fun Level?.distance(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double =
    sqrt(distanceSquared(x1, y1, z1, x2, y2, z2))

fun Level?.distance(v1: Position, v2: Position): Double =
    sqrt(distanceSquared(v1, v2))

/**
 * Calculates squared distance including ships.
 *
 * Transforms the points into world space, then calculates the squared distance
 * between them.
 */
fun Level?.distanceSquared(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double {
    var inWorldX1 = x1
    var inWorldY1 = y1
    var inWorldZ1 = z1
    var inWorldX2 = x2
    var inWorldY2 = y2
    var inWorldZ2 = z2

    val ship1 = this.getShipManagingBlock(x1, y1, z1)
    val ship2 = this.getShipManagingBlock(x2, y2, z2)

    // Do this transform manually to avoid allocation
    if (ship1 != null && ship2 != null && ship1 != ship2) {
        ship1.shipToWorld.transformPositionInline(x1, y1, z1) { x, y, z ->
            inWorldX1 = x
            inWorldY1 = y
            inWorldZ1 = z
        }
        ship2.shipToWorld.transformPositionInline(x2, y2, z2) { x, y, z ->
            inWorldX2 = x
            inWorldY2 = y
            inWorldZ2 = z
        }
    }

    val dx = inWorldX2 - inWorldX1
    val dy = inWorldY2 - inWorldY1
    val dz = inWorldZ2 - inWorldZ1

    return dx * dx + dy * dy + dz * dz
}

/**
 * Variant of [Vec3.distanceToSqr] including ships.
 */
fun Level?.distanceSquared(v1: Position, v2: Position): Double =
    distanceSquared(v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z())

/**
 * Variant of [Vec3i.distSqr] including ships.
 */
fun Level?.distanceSquared(v1: Vec3i, v2: Vec3i): Double =
    distanceSquared(v1.x.toDouble(), v1.y.toDouble(), v1.z.toDouble(),
        v2.x.toDouble(), v2.y.toDouble(), v2.z.toDouble())

/**
 * Variant of [Vec3i.distToCenterSqr] including ships.
 */
fun Level?.distanceToCenterSquared(v1: Vec3i, x2: Double, y2: Double, z2: Double): Double =
    distanceSquared(v1.x.toDouble() + 0.5, v1.y.toDouble() + 0.5, v1.z.toDouble() + 0.5, x2, y2, z2)

/**
 * Variant of [Vec3i.distToCenterSqr] including ships.
 */
fun Level?.distanceToCenterSquared(v1: Vec3i, v2: Position): Double =
    distanceToCenterSquared(v1, v2.x(), v2.y(), v2.z())

/**
 * Variant of [Vec3.closerThan] including ships.
 */
fun Level?.closerThan(v1: Position, v2: Position, distance: Double): Boolean =
    distanceSquared(v1, v2) < distance.squared()

/**
 * Variant of [Vec3i.closerThan] including ships
 */
fun Level?.closerThan(v1: Vec3i, v2: Vec3i, distance: Double): Boolean =
    distanceSquared(v1, v2) < distance.squared()

/**
 * Variant of [Vec3i.closerToCenterThan] including ships
 */
fun Level?.closerToCenterThan(v1: Vec3i, x2: Double, y2: Double, z2: Double, distance: Double): Boolean =
    distanceToCenterSquared(v1, x2, y2, z2) < distance.squared()

/**
 * Variant of [Vec3i.closerToCenterThan] including ships
 */
fun Level?.closerToCenterThan(v1: Vec3i, v2: Position, distance: Double): Boolean =
    distanceToCenterSquared(v1, v2) < distance.squared()

// region Private utilities

private fun Double.squared() = this * this

/**
 * Transform a position without allocating any intermediate objects
 */
@OptIn(ExperimentalContracts::class)
private inline fun <T> Matrix4dc.transformPositionInline(
    x: Double,
    y: Double,
    z: Double,
    transformed: (Double, Double, Double) -> T
): T {
    contract {
        callsInPlace(transformed, EXACTLY_ONCE)
    }
    return transformed(
        m00() * x + m10() * y + m20() * z + m30(),
        m01() * x + m11() * y + m21() * z + m31(),
        m02() * x + m12() * y + m22() * z + m32()
    )
}

// endregion

// region Vector Conversions

// region JOML

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3i.set(v: Vec3i) = also {
    x = v.x
    y = v.y
    z = v.z
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3d.set(v: Vec3i) = also {
    x = v.x.toDouble()
    y = v.y.toDouble()
    z = v.z.toDouble()
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3f.set(v: Vec3i) = also {
    x = v.x.toFloat()
    y = v.y.toFloat()
    z = v.z.toFloat()
}

/**
 * Sets the x, y, and z components to match the supplied vector [v].
 *
 * @return this
 */
fun Vector3d.set(v: Position) = also {
    x = v.x()
    y = v.y()
    z = v.z()
}

/**
 * Sets the minX, minY, minZ, maxX, maxY, and maxZ components to match the
 * supplied aabb [v].
 *
 * @return this
 */
fun AABBd.set(v: AABB) = also {
    minX = v.minX
    minY = v.minY
    minZ = v.minZ
    maxX = v.maxX
    maxY = v.maxY
    maxZ = v.maxZ
}

/**
 * Converts a [Vector3ic] to a [BlockPos].
 *
 * @return a new [BlockPos] with x, y, and z components matching this.
 */
fun Vector3ic.toBlockPos() = BlockPos(x(), y(), z())

/**
 * Converts a [Vector3dc] to a [Vec3].
 *
 * @return a new [Vec3] with x, y, and z components matching this.
 */
fun Vector3dc.toMinecraft() = Vec3(x(), y(), z())

/**
 * Converts an [AABBdc] to an [AABB].
 *
 * @return a new [AABB] with minX, minY, minZ, maxX, maxY, and maxZ components
 * matching this.
 */
fun AABBdc.toMinecraft() = AABB(minX(), minY(), minZ(), maxX(), maxY(), maxZ())

/**
 * Converts an [AABB] to an [AABBd].
 *
 * @return a new [AABBd] with minX, minY, minZ, maxX, maxY, and maxZ components
 * matching this.
 */
fun AABB.toJOML() = AABBd().set(this)

fun Vector2ic.toChunkPos() = ChunkPos(x(), y())
fun ChunkPos.toJOML() = Vector2i().set(this)

fun Vec3.toJOML() = Vector3d().set(this)

fun Vector3d.set(v: Vec3) = also {
    x = v.x
    y = v.y
    z = v.z
}

fun Vector2i.set(pos: ChunkPos) = also {
    x = pos.x
    y = pos.z
}

@JvmOverloads
fun Matrix4dc.transformDirection(v: Vec3i, dest: Vector3d = Vector3d()): Vector3d =
    transformDirection(dest.set(v.x.toDouble(), v.y.toDouble(), v.z.toDouble()))

@JvmOverloads
fun Matrix4dc.transformDirection(dir: Direction, dest: Vector3d = Vector3d()) = transformDirection(dir.normal, dest)

fun Matrix4dc.transform(v: Vector4f) = v.also {
    it.set(
        (m00() * v.x() + m01() * v.y() + m02() * v.z() + m03() * v.w()).toFloat(),
        (m10() * v.x() + m11() * v.y() + m12() * v.z() + m13() * v.w()).toFloat(),
        (m20() * v.x() + m21() * v.y() + m22() * v.z() + m23() * v.w()).toFloat(),
        (m30() * v.x() + m31() * v.y() + m32() * v.z() + m33() * v.w()).toFloat()
    )
}

/**
 * Transforms the position [v] by this.
 *
 * @return a new [Vec3] representing the transformed position
 *
 * @see Matrix4dc.transformPosition
 */
fun Matrix4dc.transformPosition(v: Position): Vec3 {
    transformPositionInline(v.x(), v.y(), v.z()) { x, y, z ->
        return Vec3(x, y, z)
    }
}

// endregion

// region Minecraft

fun PoseStack.multiply(modelTransform: Matrix4dc, normalTransform: Quaterniondc) = also {
    val last = last()

    val newPose = Matrix4d().set(last.pose()).mul(modelTransform)
    val newNormal = last.normal().mul(Matrix3f().set(normalTransform))

    last.pose().set(newPose)
    last.normal().set(newNormal)
}

fun PoseStack.multiply(modelTransform: Matrix4dc) = also {
    val last = last()
    val newPose = Matrix4d().set(last.pose()).mul(modelTransform)
    last.pose().set(newPose)
}

fun Vec3i.toJOML() = Vector3i().set(this)
fun Vec3i.toJOMLd() = Vector3d().set(this)
fun Vec3i.toJOMLf() = Vector3f().set(this)

fun Position.toJOML() = Vector3d().set(this)

fun Quaterniondc.toFloat() = Quaternionf(x(), y(), z(), w())
// endregion

// endregion
