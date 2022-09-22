package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.BlockTags
import net.minecraft.tags.Tag
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.game.VSBlockType
import org.valkyrienskies.core.util.logger
import org.valkyrienskies.mod.common.BlockStateInfoProvider
import org.valkyrienskies.mod.event.RegistryEvents

private data class VSBlockStateInfo(
    val id: ResourceLocation, val priority: Int, val mass: Double, val type: VSBlockType?
)

object MassDatapackResolver : BlockStateInfoProvider {
    private val map = hashMapOf<ResourceLocation, VSBlockStateInfo>()
    val loader get() = VSMassDataLoader()

    override val priority: Int
        get() = 100

    override fun getBlockStateMass(blockState: BlockState): Double? =
        map[Registry.BLOCK.getKey(blockState.block)]?.mass

    override fun getBlockStateType(blockState: BlockState): VSBlockType? =
        map[Registry.BLOCK.getKey(blockState.block)]?.type

    class VSMassDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_mass") {
        private val tags = mutableListOf<VSBlockStateInfo>()

        override fun apply(
            objects: MutableMap<ResourceLocation, JsonElement>?,
            resourceManager: ResourceManager?,
            profiler: ProfilerFiller?
        ) {
            map.clear()
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
                    logger.error(e)
                }
            }
        }

        init {
            RegistryEvents.onTagsLoaded {
                tags.forEach { tagInfo ->
                    val tag: Tag<Block>? = BlockTags.getAllTags().getTag(tagInfo.id)

                    if (tag == null) {
                        logger.warn("No specified tag '${tagInfo.id}' doesn't exist!")
                        return@forEach
                    }

                    tag.values.forEach {
                        add(
                            VSBlockStateInfo(Registry.BLOCK.getKey(it), tagInfo.priority, tagInfo.mass, tagInfo.type)
                        )
                    }
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
            if (map.containsKey(info.id)) {
                if (map[info.id]!!.priority < info.priority) {
                    map[info.id] = info
                }
            } else {
                map[info.id] = info
            }
        }

        private fun parse(element: JsonElement, origin: ResourceLocation) {
            val tag = element.asJsonObject["tag"]?.asString
            val weight = element.asJsonObject["mass"]?.asDouble
                ?: throw IllegalArgumentException("No mass in file $origin")

            val priority = element.asJsonObject["priority"]?.asInt ?: 100

            if (tag != null) {
                addToBeAddedTags(VSBlockStateInfo(ResourceLocation(tag), priority, weight, null))
            } else {
                val block = element.asJsonObject["block"]?.asString
                    ?: throw IllegalArgumentException("No block or tag in file $origin")

                add(VSBlockStateInfo(ResourceLocation(block), priority, weight, null))
            }
        }
    }

    private val logger by logger()
}
