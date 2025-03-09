package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonElement
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.util.logger

object VSEntityHandlerDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_entities") {

    override fun apply(
        list: MutableMap<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {

        list.forEach { (l, v) ->
            try {
                val type = BuiltInRegistries.ENTITY_TYPE.getOptional(l).orElse(null) ?: return@forEach
                val handler = VSEntityManager.getHandler(ResourceLocation.parse(v.asJsonObject.get("handler").asString))
                    ?: throw Exception("Handler not found")

                VSEntityManager.pair(type, handler)
            } catch (e: Exception) {
                logger.error("Error loading entity handler data for entity type $l", e)
            }
        }
    }

    private val logger by logger()
}
