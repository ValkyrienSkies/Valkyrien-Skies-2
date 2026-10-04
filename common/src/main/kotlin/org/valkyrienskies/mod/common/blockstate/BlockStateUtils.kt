@file:JvmName("BlockStateUtils")
package org.valkyrienskies.mod.common.blockstate

import de.bluecolored.bluemap.core.util.math.Axis
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getVsiBlockState
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getVsiBlockType
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.vsCore
import oshi.util.tuples.Pair
import kotlin.math.roundToInt


fun BlockGetter.getVsiBlockState(blockPos: BlockPos): VsiBlockState {
    return getVsiBlockState(getBlockState(blockPos))
}

fun BlockGetter.getVsiBlockType(blockPos: BlockPos): VsiBlockType {
    return getVsiBlockType(getBlockState(blockPos))
}

fun AABBic.size(): Int {
    return (maxX() - minX()) * (maxY() - minY()) * (maxZ() - minZ())
}

val BlockState.vsState: VsiBlockState
    get() = getVsiBlockState(this)

val BlockState.vsType: VsiBlockType
    get() = getVsiBlockType(this)

val BlockState.mass: Double
    get() {
        val composition = getComposition(this)
        return when (composition) { // if the composition is solid, mixed, or liquid, the corresponding states should never be null.
            Composition.SOLID -> vsState.solidState!!.mass
            Composition.MIXED -> vsState.solidState!!.mass + vsState.liquidState!!.actualMass
            Composition.LIQUID -> vsState.liquidState!!.actualMass
            Composition.AIR, Composition.EMPTY -> 0.0
        }
    }

val LiquidState.actualMass: Double
    get() = density * (shape.boundingBox.size().toDouble() / BlockShapeUtil.fullLodBoundingBox.size().toDouble())

fun getTypeByComposition(blockState: BlockState) =
    when (getComposition(blockState)) {
        Composition.SOLID, Composition.MIXED -> vsCore.blockTypes.solid
        Composition.LIQUID -> vsCore.blockTypes.liquid
        Composition.AIR, Composition.EMPTY -> vsCore.blockTypes.air
    }

fun BlockState.toFullString(): String = BlockStateParser.serialize(this)
fun FluidState.toFullString(): String = serializeFluid(this)

fun serializeFluid(fluidState: FluidState): String {
    val stringBuilder = StringBuilder(fluidState.holder().unwrapKey().map { key -> key.location().toString() }.orElse("empty"))
    if (fluidState.properties.isNotEmpty()) {
        stringBuilder.append('[')
        var afterFirst = false

        fun <T : Comparable<T>> appendProperty(property: Property<T>, comparable: Any) {
            stringBuilder.append(property.name)
            stringBuilder.append('=')
            @Suppress("UNCHECKED_CAST")
            stringBuilder.append(property.getName(comparable as T))
        }

        for ((property, value) in fluidState.values.entries) {
            if (afterFirst) {
                stringBuilder.append(',')
            }

            appendProperty(property, value)
            afterFirst = true
        }

        stringBuilder.append(']')
    }

    return stringBuilder.toString()
}

fun BlockState.idAndProperties() = idAndProperties(BlockStateParser.serialize(this))
fun FluidState.idAndProperties() = idAndProperties(serializeFluid(this))

fun idAndProperties(raw: String): Pair<ResourceLocation, String> {
    if (raw.indexOf('[') == -1) {
        return Pair(ResourceLocation.of(raw, ':'), "default")
        // if a blockstate has no properties, the parser will not append the brackets to it, so we can use that as a check
        // since indexOf returns -1 if the char does not exist in the string.
    }
    val id = ResourceLocation(raw.substring(0, raw.indexOf('[')))
    val properties = raw.substring(raw.indexOf('[') + 1, raw.indexOf(']'))
    return Pair(id, properties)
}

enum class Composition {
    SOLID,
    MIXED,
    LIQUID,
    AIR,
    EMPTY
}

fun getComposition(blockState: BlockState): Composition {
    if (blockState.isAir) return Composition.AIR

    val collisionShape = BlockShapeUtil.getCollisionShape(blockState)
    val outlineShape = BlockShapeUtil.getShape(blockState)
    if (collisionShape.isEmpty && !outlineShape.isEmpty) { // no collision but yes outline
        return Composition.EMPTY // for smthn like grass
    }

    val isSolid = !collisionShape.isEmpty
    val hasFluid = !blockState.fluidState.isEmpty

    return if (hasFluid)
        if (isSolid) Composition.MIXED
        else Composition.LIQUID
    else Composition.SOLID
}

fun fluidBox(fluidState: FluidState): AABBic {
    val fluidHeight = if (fluidState.isSource) {
        15
    } else {
        ((fluidState.ownHeight * 16.0).roundToInt() - 1).coerceIn(0, 15)
    }
    return AABBi(0, 0, 0, 15, fluidHeight, 15)
}

fun buildMediumState(dragCoefficient: Double, shape: AABBic): LiquidState {
    return vsCore.newLiquidStateBuilder()
        .density(0.0)
        .dragCoefficient(dragCoefficient)
        .boxShape(shape)
        .velocity(Vector3d())
        .build()
}
