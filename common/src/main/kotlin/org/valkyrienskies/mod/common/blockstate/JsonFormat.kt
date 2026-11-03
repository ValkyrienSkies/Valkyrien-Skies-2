package org.valkyrienskies.mod.common.blockstate

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.CollisionPoint
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.getAll
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.hasAny
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.isNumber
import org.valkyrienskies.mod.common.blockstate.BlockStateInfoResolver.isString
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.DensityFormat
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.DragFormat
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.ElasticityFormat
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.FrictionFormat
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.HardnessFormat
import org.valkyrienskies.mod.common.blockstate.NumericValueFormat.MassFormat
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.vsCore
import java.util.function.Consumer

data class ParseResult<out T>(
    val value: T?,
    val warnings: List<String>,
    val isError: Boolean
) {
    companion object {
        fun <T> success(value: T) = ParseResult(value, emptyList(), false)
        fun <T> success(value: T, warning: String) = ParseResult(value, listOf(warning), false)
        fun <T> success(value: T, warnings: List<String>) = ParseResult(value, warnings, false)
        fun error(error: String) = ParseResult(null, listOf(error), true)
        fun error(error: List<String>) = ParseResult(null, error, true)
    }
}

// region base formats
interface JsonFormat<T> {
    fun parse(json: JsonElement?): ParseResult<T>

    fun parse(json: JsonElement?, warningsCallback: Consumer<List<String>>): ParseResult<T> {
        val result = parse(json)
        warningsCallback.accept(result.warnings)
        return result
    }
}

class ListFormat<T>(val elementFormat: JsonFormat<T>) : JsonFormat<List<T>> {
    override fun parse(json: JsonElement?): ParseResult<List<T>> {
        if (json == null || json.isJsonNull) return ParseResult.error("null value")

        if (json.isJsonArray) {
            val array = json.asJsonArray
            val parsed = mutableListOf<T>()
            val warns = mutableListOf<String>()

            for (element in array) {
                val result = elementFormat.parse(element)
                warns.addAll(result.warnings)
                if (!result.isError) parsed.add(result.value!!)
            }
            if (parsed.isEmpty()) return ParseResult.error("No valid elements in array!")

            return ParseResult.success(parsed, warns)
        }

        return ParseResult.error("invalid format!")
    }
}

open class GenericTupleFormat<T>(
    val func: (JsonArray) -> T,
    val checks: List<(JsonArray) -> String?> = emptyList()
) : JsonFormat<T> {
    override fun parse(json: JsonElement?): ParseResult<T> {
        if (json == null || json.isJsonNull) return ParseResult.error("null value")

        if (json.isJsonArray) {
            val array = json.asJsonArray
            checks.firstNotNullOfOrNull { it(array) }?.let { return ParseResult.error(it) }

            return ParseResult.success(func(array))
        }

        return ParseResult.error("invalid format!")
    }

    companion object {
        fun checkSize(size: Int): (JsonArray) -> String? =
            { array -> if (array.size() == size) "Array must have $size elements!" else null }

        val checkNumber: (JsonArray) -> String? =
            { array -> if (array.any { !it.isNumber }) "Array elements must be numbers!" else null }
    }
}

object BoxFormat : GenericTupleFormat<AABBic>(
    {
        AABBi(it[0].asInt, it[1].asInt, it[2].asInt,
            it[3].asInt, it[4].asInt, it[5].asInt)
    },
    listOf(
        checkSize(6), checkNumber,
        { array -> if (array.any { it.asInt !in 0..15 }) "Values in box arrays must be within the range [0, 15]!" else null }
    )
) {
    val listFormat = ListFormat(BoxFormat)
}

@OptIn(PhysTickOnly::class)
object CollisionPointFormat : GenericTupleFormat<CollisionPoint>(
    { CollisionPoint(it[0].asDouble, it[1].asDouble, it[2].asDouble, it[3].asDouble) },
    listOf(checkSize(4), checkNumber)
) {
    val listFormat = ListFormat(CollisionPointFormat)
}

object SolidShapeFormat : JsonFormat<SolidBlockShape> {
    @OptIn(PhysTickOnly::class)
    override fun parse(json: JsonElement?): ParseResult<SolidBlockShape> {
        if (json == null || json.isJsonNull) return ParseResult.error("null value")

        if (json.isString) {
            val string = json.asString
            return when (string) {
                "empty" -> ParseResult.success(BlockShapeUtil.noCollisionShape)
                "full" -> ParseResult.success(BlockShapeUtil.fullBlockCollisionShape)
                else -> ParseResult.error("only \"empty\" and \"full\" are valid strings for shape!")
            }
        }

        if (json.isJsonArray) {
            val array = json.asJsonArray
            if (array.all { it.isJsonArray }) { // array of positive boxes
                val result = BoxFormat.listFormat.parse(array)
                if (result.isError) return ParseResult.error("no valid boxes for shape!")

                val shape = vsCore.solidShapeUtils.generateShapeFromBoxes(result.value!!.toMutableList()) ?: return ParseResult.error("failed to generate shape from boxes!")

                return ParseResult.success(shape)
            }

            val result = BoxFormat.parse(json)
            if (result.isError) return result as ParseResult<Nothing>
            val boxes = vsCore.solidShapeUtils.generateShapeFromBoxes(mutableListOf(result.value!!)) ?: return ParseResult.error("failed to generate shape from boxes!")

            return ParseResult.success(boxes)
        }

        if (json.isJsonObject) {
            val obj = json.asJsonObject
            if (!obj.hasAny("negative", "positive", "points"))
                return ParseResult.error("no valid keys for object shape format!")

            val posResult = obj["positive"].let { BoxFormat.listFormat.parse(it) }
            val negResult = obj["negative"].let { BoxFormat.listFormat.parse(it) }
            val pointResult = obj["points"].let { CollisionPointFormat.listFormat.parse(it) }

            // maximum values from solid state utils, but they're private values in there
            val posBoxes = vsCore.solidShapeUtils.mergeBoxes((posResult.value ?: listOf()).toMutableList())
            if (posBoxes.size > 10) return ParseResult.error("too many positive boxes (${posBoxes.size}), maximum is 10!")

            val negBoxes = vsCore.solidShapeUtils.mergeBoxes((negResult.value ?: listOf()).toMutableList())
            if (negBoxes.size > 10) return ParseResult.error("too many negative boxes (${negBoxes.size}), maximum is 10!")

            val collisionPoints = pointResult.value ?: vsCore.solidShapeUtils.generateCollisionPointsForBoxes(posBoxes)
            if (collisionPoints.size > 20) return ParseResult.error("too many collision points (${collisionPoints.size}), maximum is 20!")

            val shape = vsCore.newSolidStateBoxesShapeBuilder()
                .addCollisionPoints(collisionPoints)
                .addNegativeBoxes(negBoxes)
                .addPositiveBoxes(posBoxes)
                .build()

            return ParseResult.success(shape, posResult.warnings + negResult.warnings + pointResult.warnings)
        }

        return ParseResult.error("invalid format!")
    }
}

sealed class NumericValueFormat private constructor(val default: () -> Double, val max: Double = Double.MAX_VALUE) : JsonFormat<NumericValue> {
    private val defaultLiteral: () -> NumericValue.Literal = { NumericValue.Literal(default()) }

    override fun parse(json: JsonElement?): ParseResult<NumericValue> {
        if (json == null || json.isJsonNull) return ParseResult.success(defaultLiteral(), "got null value, using default value")

        if (json.isJsonPrimitive) {
            val p = json.asJsonPrimitive
            return when {
                p.isNumber -> {
                    val value = p.asDouble
                    if (value < 0) {
                        ParseResult.success(defaultLiteral(), "negative numbers are not valid, using default value.")
                    } else if (value > max) {
                        ParseResult.success(defaultLiteral(), "value $value is larger than the maximum value ($max), using default value.")
                    } else {
                        ParseResult.success(NumericValue.Literal(value))
                    }
                }
                p.isString -> reference(p.asString, 1.0)
                else -> ParseResult.success(defaultLiteral(), "expected number or reference string, using default value.")
            }
        }

        if (json.isJsonObject) {
            val obj = json.asJsonObject
            val target = obj["value"]
            if (target == null || !target.isString)
                return ParseResult.success(defaultLiteral(), "dependent value needs a string \"value\", using default value.")

            val multElement = obj["mult"]
            val mult = when {
                multElement == null -> 1.0
                multElement.isJsonPrimitive && multElement.asJsonPrimitive.isNumber -> multElement.asDouble
                else -> return ParseResult.success(defaultLiteral(), "expected a number for mult, using default value.")
            }
            return reference(target.asString, mult)
        }

        return ParseResult.success(defaultLiteral(), "expected a number, string, or object, using default value.")
    }

    override fun parse(json: JsonElement?, warningsCallback: Consumer<List<String>>): ParseResult<NumericValue> {
        val result = parse(json)
        warningsCallback.accept(result.warnings)
        return result
    }

    private fun reference(target: String, mult: Double): ParseResult<NumericValue> {
        if (target == "default") return ParseResult.success(NumericValue.Default(mult))
        val (id, state) = idAndProperties(target).let { it.a to it.b }
        return ParseResult.success(NumericValue.Dependent(id, state, mult))
    }

    object MassFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultBlockMass })
    object FrictionFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultBlockFriction }, 1.0)
    object ElasticityFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultBlockElasticity }, 1.0)
    object HardnessFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultBlockHardness }, 1.0)
    object DensityFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultLiquidDensity })
    object DragFormat : NumericValueFormat({ VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient }, 1.0)
}
// endregion

object SolidFormat : JsonFormat<PendingSolidProperties> {
    private val keys = listOf("mass", "friction", "elasticity", "hardness", "no_collision", "shape")
    override fun parse(json: JsonElement?): ParseResult<PendingSolidProperties> {
        if (json == null || json.isJsonNull) return ParseResult.error("null value")
        if (!json.isJsonObject) return ParseResult.error("Solid states must be json objects!")

        val obj = json.asJsonObject
        if (!obj.hasAny(keys)) return ParseResult.error("Solid state does not have any valid keys!")
        val allKeys = obj.getAll(keys)
        val warns = mutableListOf<String>()

        val shapeOverride = SolidShapeFormat.parse(allKeys["shape"]) { warns.addAll(it) }

        val mass = MassFormat.parse(allKeys["mass"]) { warns.addAll(it) }
        val friction = FrictionFormat.parse(allKeys["friction"]) { warns.addAll(it) }
        val elasticity = ElasticityFormat.parse(allKeys["elasticity"]) { warns.addAll(it) }
        val hardness = HardnessFormat.parse(allKeys["hardness"]) { warns.addAll(it) }

        return ParseResult.success(PendingSolidProperties(
            mass.value!!, friction.value!!, elasticity.value!!, hardness.value!!, shapeOverride.value
        ), warns)
    }
}

object LiquidFormat : JsonFormat<PendingLiquidProperties> {
    private val keys = listOf("density", "drag")
    override fun parse(json: JsonElement?): ParseResult<PendingLiquidProperties> {
        if (json == null || json.isJsonNull) return ParseResult.error("null value")
        if (!json.isJsonObject) return ParseResult.error("Liquid states must be json objects!")

        val obj = json.asJsonObject
        if (!obj.hasAny(keys)) return ParseResult.error("Liquid state does not have any valid keys!")
        val allKeys = obj.getAll(keys)
        val warns = mutableListOf<String>()

        val density = DensityFormat.parse(allKeys["density"]) { warns.addAll(it) }
        val drag = DragFormat.parse(allKeys["drag"]) { warns.addAll(it) }

        return ParseResult.success(PendingLiquidProperties(
            density.value!!, drag.value!!
        ), warns)
    }
}
