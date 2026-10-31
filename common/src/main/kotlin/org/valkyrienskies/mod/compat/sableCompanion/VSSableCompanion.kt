package org.valkyrienskies.mod.compat.sableCompanion

import dev.ryanhcode.sable.companion.SableCompanion
import dev.ryanhcode.sable.companion.SableCompanion.LoadPriority
import dev.ryanhcode.sable.companion.SubLevelAccess
import dev.ryanhcode.sable.companion.math.BoundingBox3dc
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.internal.VsiCoreClient
import org.valkyrienskies.mod.api.getShipById
import org.valkyrienskies.mod.api.positionToShip
import org.valkyrienskies.mod.api.toBlockPos
import org.valkyrienskies.mod.api.toJOML
import org.valkyrienskies.mod.api.toMinecraft
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.isChunkInShipyard
import org.valkyrienskies.mod.common.squaredDistanceBetweenInclShips
import org.valkyrienskies.mod.common.toWorldCoordinates
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import java.util.function.BiFunction
import java.util.function.Supplier
import kotlin.math.abs

@LoadPriority(501)
class VSSableCompanion : SableCompanion {
    override fun getAllIntersecting(
        level: Level, bounds: BoundingBox3dc
    ): Iterable<SubLevelAccess> {
        if (VSGameConfig.COMMON.SABLE_COMPANION.disableShipSublevelAccess)
            return listOf()
        return level.getShipsIntersecting(bounds.toMojang()).map { ShipSublevelAccess(it) }
    }

    override fun getContaining(
        level: Level, chunkX: Int, chunkZ: Int
    ): SubLevelAccess? {
        if (VSGameConfig.COMMON.SABLE_COMPANION.disableShipSublevelAccess)
            return null
        val ship = level.getShipManagingPos(chunkX, chunkZ) ?: return null
        return ShipSublevelAccess(ship)
    }

    override fun projectOutOfSubLevel(
        level: Level, pos: Vector3dc, dest: Vector3d
    ): Vector3d {
        val ship = level.getShipManagingPos(pos) ?: return dest
        return ship.toWorldCoordinates(dest)
    }

    override fun projectOutOfSubLevel(
        level: Level, pos: Vec3
    ): Vec3 {
        val ship = level.getShipManagingPos(pos) ?: return pos
        return ship.toWorldCoordinates(pos)
    }

    override fun <T, S : SubLevelAccess> runIncludingSubLevels(
        level: Level, origin: Vec3,
        shouldCheckOrigin: Boolean, subLevel: S?,
        converter: BiFunction<S?, BlockPos, T>
    ): T? {
        return this.runIncludingSubLevels(level, origin as Position, shouldCheckOrigin, subLevel, converter)
    }

    override fun <T, S : SubLevelAccess> runIncludingSubLevels(
        level: Level, origin: Position, shouldCheckOrigin: Boolean, subLevel: S?,
        converter: BiFunction<S?, BlockPos, T>
    ): T? {

        if (shouldCheckOrigin) {
            val ret = converter.apply(subLevel, BlockPos.containing(origin))
            if (ret != null)
                return ret
        }
        val worldPos = origin.toJOML()

        if (subLevel != null) {
            val ship = (subLevel as ShipSublevelAccess).ship
            val ret = converter.apply(subLevel, ship.shipToWorld.transformPosition(worldPos).toBlockPos())
            if (ret != null)
                return ret
        }

        if (VSGameConfig.COMMON.SABLE_COMPANION.disableShipSublevelAccess)
            return null

        val ship = if (subLevel == null) null else (subLevel as ShipSublevelAccess).ship
        level.getShipsIntersecting(
            AABBd(
                origin.x(), origin.y(), origin.z(),
                origin.x() + 1, origin.y() + 1, origin.z() + 1
            )
        ).forEach {
            if (it == ship)
                return@forEach
            @Suppress("UNCHECKED_CAST")
            val ret = converter.apply(ShipSublevelAccess(it) as S, it.positionToShip(worldPos).toBlockPos())
            if (ret != null)
                return ret
        }
        return null
    }

    override fun <S : SubLevelAccess> findIncludingSubLevels(
        level: Level, origin: Vec3,
        shouldCheckOrigin: Boolean, subLevel: S?,
        converter: BiFunction<S?, BlockPos, Boolean>
    ): Boolean {
        return runIncludingSubLevels(level, origin, shouldCheckOrigin, subLevel, converter) ?: false
    }

    override fun distanceSquaredWithSubLevels(
        level: Level, a: Vector3dc, b: Vector3dc
    ): Double {
        return level.squaredDistanceBetweenInclShips(
            a.x(), a.y(), a.z(),
            b.x(), b.y(), b.z()
        )
    }

    override fun distanceSquaredWithSubLevels(
        level: Level, a: Position, b: Position
    ): Double {
        return level.squaredDistanceBetweenInclShips(
            a.x(), a.y(), a.z(),
            b.x(), b.y(), b.z()
        )
    }

    override fun distanceSquaredWithSubLevels(
        level: Level?, a: Vector3dc, bX: Double, bY: Double,
        bZ: Double
    ): Double {
        return level.squaredDistanceBetweenInclShips(
            a.x(), a.y(), a.z(),
            bX, bY, bZ
        )
    }

    override fun distanceSquaredWithSubLevels(
        level: Level?, a: Position, bX: Double, bY: Double,
        bZ: Double
    ): Double {
        return level.squaredDistanceBetweenInclShips(
            a.x(), a.y(), a.z(),
            bX, bY, bZ
        )
    }

    override fun distanceSquaredWithSubLevels(
        level: Level?, aX: Double, aY: Double, aZ: Double,
        bX: Double, bY: Double, bZ: Double
    ): Double {
        return level.squaredDistanceBetweenInclShips(
            aX, aY, aZ,
            bX, bY, bZ
        )
    }

    override fun rectilinearDistanceWithSubLevels(
        level: Level, a: Vector3dc, b: Vector3dc
    ): Double {
        val a = level.toWorldCoordinates(Vector3d(a))
        val b = level.toWorldCoordinates(Vector3d(b))
        return abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
    }

    override fun rectilinearDistanceWithSubLevels(
        level: Level, a: Position, b: Position
    ): Double {
        val a = level.toWorldCoordinates(a.x(), a.y(), a.z())
        val b = level.toWorldCoordinates(b.x(), b.y(), b.z())
        return abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
    }

    override fun rectilinearDistanceWithSubLevels(
        level: Level, a: Vector3dc, bX: Double, bY: Double,
        bZ: Double
    ): Double {
        val a = level.toWorldCoordinates(Vector3d(a))
        val b = level.toWorldCoordinates(bX, bY, bZ)
        return abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
    }

    override fun rectilinearDistanceWithSubLevels(
        level: Level, a: Position, bX: Double, bY: Double,
        bZ: Double
    ): Double {
        val a = level.toWorldCoordinates(a.x(), a.y(), a.z())
        val b = level.toWorldCoordinates(bX, bY, bZ)
        return abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
    }

    override fun rectilinearDistanceWithSubLevels(
        level: Level, aX: Double, aY: Double, aZ: Double,
        bX: Double, bY: Double, bZ: Double
    ): Double {
        val a = level.toWorldCoordinates(aX, aY, aZ)
        val b = level.toWorldCoordinates(bX, bY, bZ)
        return abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)
    }

    override fun getVelocity(
        level: Level, pos: Vector3dc, dest: Vector3d
    ): Vector3d {
        val ship = level.getShipManagingPos(pos) ?: return dest
        return dest.set(ship.kinematics.velocity)
    }

    override fun getVelocity(
        level: Level, pos: Vec3
    ): Vec3 {
        val ship = level.getShipManagingPos(pos) ?: return Vec3.ZERO
        return ship.kinematics.velocity.toMinecraft()
    }

    override fun getVelocity(
        level: Level, subLevel: SubLevelAccess,
        pos: Vector3dc, dest: Vector3d
    ): Vector3d {
        return dest.set((subLevel as ShipSublevelAccess).ship.kinematics.velocity)
    }

    override fun getVelocity(
        level: Level, subLevel: SubLevelAccess,
        pos: Vec3
    ): Vec3 {
        return (subLevel as ShipSublevelAccess).ship.kinematics.velocity.toMinecraft()
    }

    override fun getVelocityRelativeToAir(
        level: Level, pos: Vector3dc, dest: Vector3d
    ): Vector3d {
        return getVelocity(level, pos, dest)
    }

    override fun getVelocityRelativeToAir(
        level: Level, pos: Vec3
    ): Vec3 {
        return getVelocity(level, pos)
    }

    override fun isInPlotGrid(
        level: Level, chunkX: Int, chunkZ: Int
    ): Boolean {
        return level.isChunkInShipyard(chunkX, chunkZ)
    }

    override fun getTrackingSubLevel(
        entity: Entity
    ): SubLevelAccess? {
        if (VSGameConfig.COMMON.SABLE_COMPANION.disableShipSublevelAccess)
            return null
        if (entity !is IEntityDraggingInformationProvider)
            return null
        val dragInfo = entity.draggingInformation
        if (!dragInfo.isEntityBeingDraggedByAShip())
            return null
        val ship = entity.level().getShipById(dragInfo.lastShipStoodOn!!) ?: return null
        return ShipSublevelAccess(ship)
    }

    override fun getLastTrackingSubLevel(
        entity: Entity
    ): SubLevelAccess? {
        if (VSGameConfig.COMMON.SABLE_COMPANION.disableShipSublevelAccess)
            return null
        if (entity !is IEntityDraggingInformationProvider)
            return null
        val dragInfo = entity.draggingInformation
        if (dragInfo.lastShipStoodOn == null)
            return null
        val ship = entity.level().getShipById(dragInfo.lastShipStoodOn!!) ?: return null
        return ShipSublevelAccess(ship)
    }

    override fun getTrackingOrVehicleSubLevel(
        entity: Entity
    ): SubLevelAccess? {
        return getTrackingSubLevel(entity.vehicle ?: entity)
    }

    override fun getVehicleSubLevel(
        entity: Entity
    ): SubLevelAccess? {
        if (entity.vehicle == null)
            return null
        return getTrackingSubLevel(entity.vehicle!!)
    }

    override fun getEyePositionInterpolated(
        entity: Entity, partialTicks: Float
    ): Vec3 {
        return entity.getEyePosition(partialTicks)
    }

    override fun getFeetPos(
        entity: Entity, distanceDown: Float
    ): Vector3d {
        return super.getFeetPos(entity, distanceDown, entity.level().getShipManagingPos(entity.position())?.transform?.rotation)
    }

    override fun getClientLevel(): Level? {
        if (ValkyrienSkiesMod.vsCore is VsiCoreClient)
            return Supplier<Level?>{ Minecraft.getInstance().level }.get()
        return null
    }
}
