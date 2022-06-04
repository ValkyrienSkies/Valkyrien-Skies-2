package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.resource.JsonDataLoader
import net.minecraft.resource.ResourceManager
import net.minecraft.tag.BlockTags
import net.minecraft.tag.Tag
import net.minecraft.util.Identifier
import net.minecraft.util.profiler.Profiler
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import org.valkyrienskies.mod.event.RegistryEvents

private data class VSBlockStateInfo(val id: Identifier, val mass: Double, val type: VSBlockType?)
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
        private val tags = mutableListOf<VSBlockStateInfo>()

        override fun apply(
            objects: MutableMap<Identifier, JsonElement>?,
            resourceManager: ResourceManager?,
            profiler: Profiler?
        ) {
            resolver.map.clear()
            objects?.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        element.asJsonArray.forEach { element1: JsonElement ->
                            parse(element1, location)
                        }
                    } else if (element.isJsonObject) {
                        parse(element, location)
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        init {
            RegistryEvents.onTagsLoaded {
                tags.forEach { tagInfo ->
                    val tag: Tag<Block>? = BlockTags.getTagGroup().getTag(tagInfo.id)

                    if (tag == null) {
                        LOGGER.warn("No specified tag '${tagInfo.id}' doesn't exist!")
                        return@forEach
                    }

                    tag.values().forEach { add(VSBlockStateInfo(Registry.BLOCK.getId(it), tagInfo.mass, tagInfo.type)) }
                }
                tags.clear()
            }
        }

        // so why does this exist? cus for some reason initializes their tags after all the other things
        // idk why, so we note them down and use them later
        private fun addToBeAddedTags(tag: VSBlockStateInfo) {
            tags.add(tag)
        }

        private fun add(info: VSBlockStateInfo) {
            resolver.map[info.id] = info
        }

        private fun parse(element: JsonElement, origin: Identifier) {
            val tag = element.asJsonObject["tag"]?.asString
            val weight = element.asJsonObject["mass"]?.asDouble
                ?: throw IllegalArgumentException("No mass in file $origin")

            if (tag != null) {
                addToBeAddedTags(VSBlockStateInfo(Identifier(tag), weight, null))
            } else {
                val block = element.asJsonObject["block"]?.asString
                    ?: throw IllegalArgumentException("No block or tag in file $origin")

                add(VSBlockStateInfo(Identifier(block), weight, null))
            }
        }

        companion object {
            val LOGGER = LogManager.getLogger()
        }
    }
}
