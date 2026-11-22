package org.valkyrienskies.mod.compat.create

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity
import com.simibubi.create.infrastructure.config.AllConfigs
import net.minecraft.core.BlockPos
import org.valkyrienskies.mod.mixinducks.mod_compat.create.ChainConveyorCrossFrameDuck
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.getShipManagingPos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Create stores a chain conveyor connection as the raw block offset to the other conveyor. Between a ship and the
 * world (or two ships) that offset spans two frames, so its geometry is replaced by where the other conveyor is now,
 * seen from this conveyor's frame. Within one frame everything stays the raw offset.
 */
object ChainConveyorFrames {

    @JvmStatic
    fun isCrossFrame(level: Level?, from: BlockPos, connection: BlockPos): Boolean {
        if (level == null) return false
        return level.getShipManagingPos(from)?.id != level.getShipManagingPos(from.offset(connection))?.id
    }

    /** Offset from [from]'s bottom centre to the other conveyor's, in [from]'s frame. */
    @JvmStatic
    fun offset(level: Level?, from: BlockPos, connection: BlockPos): Vec3 {
        val raw = Vec3.atLowerCornerOf(connection)
        if (level == null) return raw
        val to = from.offset(connection)
        val fromShip = level.getShipManagingPos(from)
        val toShip = level.getShipManagingPos(to)
        if (fromShip?.id == toShip?.id) return raw
        val target = Vec3.atBottomCenterOf(to).let { p -> toShip?.let { toWorld(it, p) } ?: p }
        val local = fromShip?.let { toShip(it, target) } ?: target
        return local.subtract(Vec3.atBottomCenterOf(from))
    }

    /** Loop angle at [at] where the chain from the conveyor at [at] + [back] arrives (Create's `tangentAngle + 180 ± 70`). */
    @JvmStatic
    fun arrivalAngle(level: Level?, at: BlockPos, back: BlockPos, reversed: Boolean): Float {
        val o = offset(level, at, back)
        return Mth.RAD_TO_DEG * Mth.atan2(o.x, o.z).toFloat() + 35f * (if (reversed) -1 else 1)
    }

    /** Create's chain cost for the connection's current length. */
    @JvmStatic
    fun chainCost(level: Level?, from: BlockPos, connection: BlockPos): Int =
        max((offset(level, from, connection).length() / 2.5).roundToInt(), 1)

    /** Chains given back for a connection: between frames, what was paid when it was made. */
    @JvmStatic
    fun refund(be: ChainConveyorBlockEntity, connection: BlockPos): Int =
        (be as ChainConveyorCrossFrameDuck).`vs$chainsPaid`(connection) ?: chainCost(be.level, be.blockPos, connection)

    /** Longest a connection between frames can stretch: what its paid chains cover (Create rounds length / 2.5). */
    @JvmStatic
    fun maxLength(be: ChainConveyorBlockEntity, connection: BlockPos): Double =
        ((be as ChainConveyorCrossFrameDuck).`vs$chainsPaid`(connection)?.let { (it + 0.5) * 2.5 }
            ?: AllConfigs.server().kinetics.maxChainConveyorLength.get().toDouble())

    /** [p] in the frame of the conveyor at [from], in the world. */
    @JvmStatic
    fun toWorld(level: Level?, from: BlockPos, p: Vec3): Vec3 =
        level?.getShipManagingPos(from)?.let { toWorld(it, p) } ?: p

    private fun toWorld(ship: Ship, p: Vec3): Vec3 {
        val m = (ship as? ClientShip)?.renderTransform?.shipToWorld ?: ship.shipToWorld
        return m.transformPosition(Vector3d(p.x, p.y, p.z)).let { Vec3(it.x, it.y, it.z) }
    }

    private fun toShip(ship: Ship, p: Vec3): Vec3 {
        val m = (ship as? ClientShip)?.renderTransform?.worldToShip ?: ship.worldToShip
        return m.transformPosition(Vector3d(p.x, p.y, p.z)).let { Vec3(it.x, it.y, it.z) }
    }
}
