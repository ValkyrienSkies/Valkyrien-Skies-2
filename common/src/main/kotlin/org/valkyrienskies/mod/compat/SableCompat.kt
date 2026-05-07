package org.valkyrienskies.mod.compat

import dev.ryanhcode.sable.companion.SableCompanion
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.Vector3dc

object SableCompat {
    fun sublevelToWorld(level: Level?, pos: Vector3dc, dest: Vector3d): Vector3d =
        SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos, dest)

    fun sublevelToWorld(level: Level?, pos: Position): Vec3 =
        SableCompanion.INSTANCE.projectOutOfSubLevel(level, pos)

    fun isChunkInSublevel(level: Level?, x: Int, y: Int) =
        SableCompanion.INSTANCE.isInPlotGrid(level, x, y)

    fun isBlockInSublevel(level: Level?, x: Int, y: Int, z: Int) =
        SableCompanion.INSTANCE.isInPlotGrid(level, BlockPos(x, y, z))
}
