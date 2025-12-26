package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.mod.util.DEFAULT_WORLD_GRAVITY
import org.valkyrienskies.mod.util.logger

object DimensionParametersResolver: SimpleJsonResourceReloadListener(Gson(), "vs_dimension_parameters") {

    val logger = logger("Bloon Factory").logger
    var dimensionMap: Map<String, Parameters> = hashMapOf()

    override fun apply(
        objects: Map<ResourceLocation?, JsonElement?>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        val temp = hashMapOf<String, Parameters>()

        objects.forEach { (key, value) ->
            if (key == null || value == null) {return@forEach}
            try {
                if (value.isJsonArray) {
                    value.asJsonArray.forEach { parse(it, temp) }
                } else if (value.isJsonObject) {
                    parse(value, temp)
                } else throw IllegalArgumentException()
            } catch (e: Exception) {
                logger.error(e.stackTraceToString())
            }
        }

        dimensionMap = temp
    }

    private fun parse(element: JsonElement, map: MutableMap<String, Parameters>) {
        val maxYPos = element.asJsonObject["maxYPos"]?.asDouble ?: throw NoSuchElementException("Parameter \"maxYPos\" wasn't filled")
        val seaLevel = element.asJsonObject["seaLevel"]?.asDouble ?: throw NoSuchElementException("Parameter \"seaLevel\" wasn't filled")
        val gravity = element.asJsonObject["gravity"]?.asJsonArray ?: throw NoSuchElementException("Parameter \"gravity\" wasn't filled")
        val dimensionId = element.asJsonObject["dimensionId"]?.asString ?: throw NoSuchElementException("Parameter \"dimensionId\" wasn't filled")
        val priority = element.asJsonObject["priority"]?.asInt ?: 0

        fun JsonArray.toVector3d() : Vector3dc {
            return Vector3d(
                this[0].asDouble,
                this[1].asDouble,
                this[2].asDouble
            )
        }

        map.getOrPut(dimensionId) { Parameters(maxYPos, seaLevel, gravity.toVector3d(), priority) }.also {
            if (it.priority < priority) {
                map[dimensionId] = Parameters(maxYPos, seaLevel, gravity.toVector3d(), priority)
            }
        }
    }

    /**
     * Data class representing the atmospheric parameters for a dimension.
     *
     * @param maxY The maximum Y value in the world, representing the top of the atmosphere. Not the same as the build height limit- this is the Y value representing the top
     * of the dimension's atmosphere, and determines the squash factor of the atmosphere's gradient
     * @param seaLevel The Y value representing sea level in the dimension. This is the Y value where the atmosphere is at its densest, and any Y levels below are equal to this value.
     * @param gravity The gravitational acceleration in the dimension, in m/s^2. Defaults to DEFAULT_WORLD_GRAVITY, or (0, -10, 0). Should be a negative value if downward.
     */
    data class Parameters(val maxY: Double, val seaLevel: Double, var gravity: Vector3dc = DEFAULT_WORLD_GRAVITY, val priority: Int = -1)
}
