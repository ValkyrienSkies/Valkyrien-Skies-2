package org.valkyrienskies.mod.common.config

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.GsonHelper
import net.minecraft.util.profiling.ProfilerFiller
import org.valkyrienskies.mod.util.logger
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

object SlugDatapackResolver {
    private val logger by logger()
    val loader = DataLoader

    private var slugList: List<SlugEntry> = listOf()
    private var slugsByPosition: List<List<SlugEntry>> = mutableListOf()

    const val NOUNS_PER_NAME = 3

    fun generateSlug(): String {
        if (slugList.isEmpty()) {
            return "unnamed-ship"
        }

        return List(NOUNS_PER_NAME) { position ->
            val choices = slugsByPosition[position].ifEmpty { slugList }
            choices[ThreadLocalRandom.current().nextInt(0, choices.size)].id
        }.joinToString("-")
    }

    /**
     * Sort each SlugEntry into the places in the slug it can go into
     */
    private fun calculatePositionalSlugs() {
        slugsByPosition = List(NOUNS_PER_NAME) { position ->
            slugList.filter { entry ->
                entry.positions.isNullOrEmpty() || position in entry.positions
            }
        }
    }

    /**
     * @param addedSlugs is a map of the resource location of each datapack file to the list of slug entries it adds.
     * @param removedSlugs is a list of string slugs which should be removed from [addedSlugs], if present, after [addedSlugs] has fully loaded.
     */
    data class LoadedSlugData(val addedSlugs: MutableMap<ResourceLocation, MutableList<SlugEntry>>, val removedSlugs: List<String>)

    data class SlugEntry(val id: String, val positions: List<Int>? = listOf())

    object DataLoader : SimplePreparableReloadListener<LoadedSlugData>() {

        override fun prepare(
            resourceManager: ResourceManager,
            profiler: ProfilerFiller
        ): LoadedSlugData {

            val result = mutableMapOf<ResourceLocation, MutableList<SlugEntry>>()
            val removed = mutableListOf<String>()

            // Every json under data/<namespace>/vs_slugs
            val resources = resourceManager.listResourceStacks("vs_slugs") {
                it.path.endsWith(".json")
            }

            for ((id, stack) in resources) {
                try {
                    val entries = mutableListOf<SlugEntry>()

                    for (resource in stack) {
                        resource.openAsReader().use { reader ->
                            //region the actual parsing of the file
                            val obj = GsonHelper.parse(reader).asJsonObject

                            if (obj.get("replace")?.asBoolean == true) {
                                entries.clear()
                            }

                            obj.get("remove")?.asJsonArray?.forEach { remove ->
                                removed.add(remove.asString)
                            }

                            obj.get("values")?.asJsonArray?.forEach { value ->
                                val entry =
                                    if (value.isJsonPrimitive) {
                                        SlugEntry(value.asString, null)
                                    } else {
                                        val json = value.asJsonObject

                                        SlugEntry(
                                            json["id"].asString,
                                            json["positions"]
                                                ?.asJsonArray
                                                ?.map {
                                                    val i = it.asInt
                                                    if (i < 0 || i > (NOUNS_PER_NAME-1)) {
                                                        logger.warn("Warning while parsing $id: Slug '${json["id"].asString}' position '$i' is not within range 0..${NOUNS_PER_NAME-1}")
                                                    }
                                                    i
                                                }
                                        )
                                    }
                                entries += entry
                            }
                            //endregion
                        }
                    }

                    result[id] = entries
                } catch (e: Exception) {
                    logger.error("An exception occurred while parsing: $id. See below for info")
                    e.printStackTrace()
                }
            }

            return LoadedSlugData(result, removed)
        }

        override fun apply(
            data: LoadedSlugData,
            resourceManager: ResourceManager,
            profiler: ProfilerFiller
        ) {
            // Hash set .contains() is slightly faster
            val removed = data.removedSlugs.toHashSet()

            slugList = data.addedSlugs.values
                .flatten()
                .filter { it.id !in removed }

            calculatePositionalSlugs()
        }
    }
}
