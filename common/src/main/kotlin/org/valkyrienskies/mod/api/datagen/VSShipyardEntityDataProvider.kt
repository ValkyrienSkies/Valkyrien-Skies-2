package org.valkyrienskies.mod.api.datagen

import com.google.gson.JsonObject
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Extend this class and implement {@link VSShipyardEntityDataProvider#registerEntities}.
 * Register an instance of the class with your platform's data generator.
 */
abstract class VSShipyardEntityDataProvider(val output: PackOutput, val modId: String) : DataProvider {
    private val entries: HashMap<ResourceLocation, CompletableFuture<*>> = HashMap()

    /**
     * Implement this method and then use {@link VSShipyardEntityDataProvider#addEntity} method.
     */
    protected abstract fun registerEntries()

    /**
     * Adds the given Entity Type ID to their Shipyard Entity datapack JSON
     *
     * @param id The Entity Type ID
     */
    protected fun addEntity(
        id: ResourceLocation
    ) {
        if (entries.contains(id))
            throw RuntimeException("Duplicate Block Into Entries for $id")
        val path: Path = output.outputFolder
            .resolve("data")
            .resolve(id.namespace)
            .resolve("vs_entities")
            .resolve(id.path + ".json")

        entries[id] = (CompletableFuture.supplyAsync {
            try {
                val json = JsonObject()
                json.addProperty("handler", "valkyrienskies:shipyard")
                return@supplyAsync DataProvider.saveStable(CachedOutput.NO_CACHE, json, path)
            } catch (e: java.lang.Exception) {
                throw java.lang.RuntimeException("Failed to save $path", e)
            }
        }.join())
    }

    /**
     * Adds the given Entity Type to their Shipyard Entity datapack JSON
     *
     * @param type The Entity Type
     */
    protected fun addEntity(
        type: EntityType<*>
    ) {
        addEntity(type.builtInRegistryHolder().key().location())
    }

    override fun run(cachedOutput: CachedOutput): CompletableFuture<*> {
        registerEntries()
        return CompletableFuture.allOf(*entries.values.toTypedArray())
    }

    override fun getName(): String {
        return "VS Shipyard Entity Data Provider: $modId"
    }
}
