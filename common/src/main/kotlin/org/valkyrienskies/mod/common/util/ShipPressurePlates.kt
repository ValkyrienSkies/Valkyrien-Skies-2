package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BasePressurePlateBlock
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.mixin.feature.ship_interactions.PressurePlateAccessor
import java.util.WeakHashMap

object ShipPressurePlates {
    private val held = WeakHashMap<ServerLevel, Map<BlockPos, BasePressurePlateBlock>>()

    fun isPushed(level: ServerLevel, pos: BlockPos, source: Ship, target: Ship?): Boolean {
        // Keep the same contact area for both plate positions.
        val sensor = AABBd(pos.x + 0.125, pos.y.toDouble(), pos.z + 0.125,
            pos.x + 0.875, pos.y + 0.25, pos.z + 0.875)
        return ShipBlockContacts.find(level, source, sensor, target, sweep = false,
            pushNormal = Vector3d(0.0, -1.0, 0.0)) != null
    }

    fun update(level: ServerLevel, plates: Map<BlockPos, BasePressurePlateBlock>) {
        val previous = held.put(level, HashMap(plates)) ?: emptyMap()
        for ((pos, block) in plates) {
            if (!level.hasChunkAt(pos)) continue
            val state = level.getBlockState(pos)
            if (state.block !== block) continue
            val accessor = block as PressurePlateAccessor
            val signal = accessor.`vs$getSignalForState`(state)
            if (signal < 15) accessor.`vs$checkPressed`(null, level, pos, state, signal)
        }
        for ((pos, block) in previous) {
            if (pos in plates || !level.hasChunkAt(pos)) continue
            val state = level.getBlockState(pos)
            if (state.block !== block) continue
            val accessor = block as PressurePlateAccessor
            // Check entities at once after the ship leaves.
            accessor.`vs$checkPressed`(null, level, pos, state, accessor.`vs$getSignalForState`(state))
        }
    }

    @JvmStatic
    fun signal(level: Level, pos: BlockPos): Int =
        if (level is ServerLevel && VSGameConfig.SERVER.ShipInteractions.shipRedstone &&
            held[level]?.containsKey(pos) == true) 15 else 0

    fun clear() = held.clear()
}
