package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.block.BlockState
import net.minecraft.resource.JsonDataLoader
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import net.minecraft.util.registry.Registry
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.game.VSBlockType.SOLID
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import java.util.function.Consumer

private data class VSBlockStateInfo(val id: Identifier, val mass: Double, val type: VSBlockType)
class MassDatapackResolver : BlockStateInfoProvider {
    private val map = hashMapOf<Identifier, VSBlockStateInfo>()
    val loader get() = VSMassDataLoader(this)

    override val priority: Int
        get() = 100

    override fun getBlockStateMass(blockState: BlockState): Double? =
        map[Registry.BLOCK.getId(blockState.block)]?.mass

    override fun getBlockStateType(blockState: BlockState): VSBlockType? =
        map[Registry.BLOCK.getId(blockState.block)]?.type

    class VSMassDataLoader(val resolver: MassDatapackResolver) : JsonDataLoader(Gson(), "vs_mass") {

        override fun apply(
            objects: MutableMap<Identifier, JsonElement>?, resourceManager: ResourceManager?, profiler: Profiler?
        ) {
            resolver.map.clear()
            objects?.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        element.asJsonArray.forEach(
                            Consumer { element1: JsonElement ->
                                add(
                                    parse(element1)
                                )
                            })
                    } else if (element.isJsonObject) {
                        add(parse(element))
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun add(info: VSBlockStateInfo) {
            resolver.map[info.id] = info
        }

        private fun parse(element: JsonElement): VSBlockStateInfo {
            val block = element.asJsonObject["block"]?.asString
            val weight = element.asJsonObject["mass"]?.asDouble
            if (weight == null || block == null) throw IllegalArgumentException()
            val blockId = Identifier(block)
            return VSBlockStateInfo(blockId, weight, SOLID)
        }
    }
}
