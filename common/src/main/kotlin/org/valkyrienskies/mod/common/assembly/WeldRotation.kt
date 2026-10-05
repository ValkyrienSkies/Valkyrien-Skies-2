package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import org.joml.Matrix3d
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import kotlin.math.abs

internal data class WeldRotation(val xAxis: Direction, val yAxis: Direction, val zAxis: Direction) {
    fun transform(x: Int, y: Int, z: Int): BlockPos = BlockPos(
        xAxis.stepX * x + yAxis.stepX * y + zAxis.stepX * z,
        xAxis.stepY * x + yAxis.stepY * y + zAxis.stepY * z,
        xAxis.stepZ * x + yAxis.stepZ * y + zAxis.stepZ * z
    )

    fun transform(direction: Direction): Direction {
        val pos = transform(direction.stepX, direction.stepY, direction.stepZ)
        return Direction.fromDelta(pos.x, pos.y, pos.z)!!
    }

    fun destination(source: BlockPos, sourceAnchor: BlockPos, targetAnchor: BlockPos, targetFace: Direction): BlockPos {
        val offset = transform(source.x - sourceAnchor.x, source.y - sourceAnchor.y, source.z - sourceAnchor.z)
        return targetAnchor.relative(targetFace).offset(offset)
    }

    fun quaternion(): Quaterniond = Quaterniond().setFromNormalized(Matrix3d(
        xAxis.stepX.toDouble(), xAxis.stepY.toDouble(), xAxis.stepZ.toDouble(),
        yAxis.stepX.toDouble(), yAxis.stepY.toDouble(), yAxis.stepZ.toDouble(),
        zAxis.stepX.toDouble(), zAxis.stepY.toDouble(), zAxis.stepZ.toDouble()
    ))

    val yaw: Rotation?
        get() = if (yAxis != Direction.UP) null else when (transform(Direction.NORTH)) {
            Direction.NORTH -> Rotation.NONE
            Direction.EAST -> Rotation.CLOCKWISE_90
            Direction.SOUTH -> Rotation.CLOCKWISE_180
            Direction.WEST -> Rotation.COUNTERCLOCKWISE_90
            else -> null
        }

    fun rotateState(state: BlockState): BlockState? {
        yaw?.let { return state.rotate(it) }
        var result = state
        for (property in state.properties) {
            when {
                property is DirectionProperty -> {
                    val direction = transform(state.getValue(property))
                    if (direction !in property.possibleValues) return null
                    result = result.setValue(property, direction)
                }
                property is EnumProperty<*> && property.valueClass == Direction.Axis::class.java -> {
                    @Suppress("UNCHECKED_CAST")
                    val axisProperty = property as Property<Direction.Axis>
                    val axis = when (state.getValue(axisProperty)) {
                        Direction.Axis.X -> xAxis.axis
                        Direction.Axis.Y -> yAxis.axis
                        Direction.Axis.Z -> zAxis.axis
                    }
                    result = result.setValue(axisProperty, axis)
                }
                property.valueClass.isEnum || property.name in ORIENTED_PROPERTIES -> return null
            }
        }
        return result
    }

    companion object {
        private val ORIENTED_PROPERTIES = setOf("north", "east", "south", "west", "up", "down", "rotation", "hanging")
        val ALL: List<WeldRotation> = buildList {
            for (x in Direction.values()) for (y in Direction.values()) {
                if (x.axis == y.axis) continue
                val z = Vector3d(x.stepX.toDouble(), x.stepY.toDouble(), x.stepZ.toDouble())
                    .cross(Vector3d(y.stepX.toDouble(), y.stepY.toDouble(), y.stepZ.toDouble()))
                add(WeldRotation(x, y, Direction.fromDelta(z.x.toInt(), z.y.toInt(), z.z.toInt())!!))
            }
        }

        fun candidates(sourceFace: Direction, targetFace: Direction, relativeRotation: Quaterniondc): List<WeldRotation> =
            ALL.filter { it.transform(sourceFace) == targetFace.opposite }
                .sortedByDescending { abs(it.quaternion().dot(relativeRotation)) }
    }
}
