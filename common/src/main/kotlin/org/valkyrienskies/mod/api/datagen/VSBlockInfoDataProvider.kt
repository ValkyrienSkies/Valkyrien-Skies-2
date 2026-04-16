package org.valkyrienskies.mod.api.datagen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

/**
 * Extend this class and implement {@link VSBlockInfoDataProvider#registerEntries}.
 * Register an instance of the class with your platform's data generator.
 */
abstract class VSBlockInfoDataProvider(val output: PackOutput, val modId: String) : DataProvider {
    private val entries: HashMap<ResourceLocation, Info> = HashMap()

    /**
     * Implement this method and then use the {@link VSBlockInfoDataProvider#addBlock} and {@link VSBlockInfoDataProvider#addBlockTag} methods.
     */
    protected abstract fun registerEntries()

    /**
     * Adds the given Block ID to the mass datapack JSON
     *
     * @param id The Block ID
     * @param mass The Mass of the Block
     * @param friction The Friction of the Block
     * @param elasticity The Elasticity of the Block
     * @param priority The Priority of this information. Higher priorities override data from lower priorities.
     */
    protected fun addBlock(
        id: ResourceLocation,
        mass: Double? = null,
        friction: Double? = null,
        elasticity: Double? = null,
        priority: Int? = null
    ) {
        entries[id]?.let { throw RuntimeException("Duplicate Block Into Entries for $id") }
        entries[id] = Info(false, mass, friction, elasticity, priority)
    }

    /**
     * Adds the given Block Tag ID to the mass datapack JSON
     *
     * @param id The Block Tag ID
     * @param mass The Mass of the Block
     * @param friction The Friction of the Block
     * @param elasticity The Elasticity of the Block
     * @param priority The Priority of this information. Higher priorities override data from lower priorities.
     */
    protected fun addBlockTag(
        id: ResourceLocation,
        mass: Double? = null,
        friction: Double? = null,
        elasticity: Double? = null,
        priority: Int? = null
    ) {
        entries[id]?.let { throw RuntimeException("Duplicate Block Into Entries for $id") }
        entries[id] = Info(true, mass, friction, elasticity, priority)
    }

    /**
     * Adds the given Block to the mass datapack JSON
     *
     * @param block The Block
     * @param mass The Mass of the Block
     * @param friction The Friction of the Block
     * @param elasticity The Elasticity of the Block
     * @param priority The Priority of this information. Higher priorities override data from lower priorities.
     */
    protected fun addBlock(
        block: Block,
        mass: Double? = null,
        friction: Double? = null,
        elasticity: Double? = null,
        priority: Int? = null
    ) {
        addBlock(block.builtInRegistryHolder().key().location(), mass, friction, elasticity, priority)
    }

    /**
     * Adds the given Block Tag to the mass datapack JSON
     *
     * @param tag The Block Tag
     * @param mass The Mass of the Block
     * @param friction The Friction of the Block
     * @param elasticity The Elasticity of the Block
     * @param priority The Priority of this information. Higher priorities override data from lower priorities.
     */
    protected fun addBlockTag(
        tag: TagKey<Block>,
        mass: Double? = null,
        friction: Double? = null,
        elasticity: Double? = null,
        priority: Int? = null
    ) {
        addBlockTag(tag.location, mass, friction, elasticity, priority)
    }

    override fun run(cachedOutput: CachedOutput): CompletableFuture<*> {
        return CompletableFuture.supplyAsync {
            val path = this.output.outputFolder
                .resolve("data")
                .resolve(ValkyrienSkiesMod.MOD_ID)
                .resolve("vs_mass")
                .resolve("$modId.json")

            registerEntries()

            try {
                val array = JsonArray(entries.size)
                entries.forEach { (id, info) -> array.add(info.toJson(id)) }

                return@supplyAsync DataProvider.saveStable(cachedOutput, array, path)
            } catch (e: Exception) {
                throw RuntimeException("Failed to save $path", e)
            }
        }.get()
    }

    override fun getName(): String {
        return "VS Block Info Data Provider: $modId.json"
    }

    private data class Info(
        val tag: Boolean,
        val mass: Double?,
        val friction: Double?,
        val elasticity: Double?,
        val priority: Int?
    ) {
        fun toJson(id: ResourceLocation): JsonObject {
            val obj = JsonObject()
            obj.add(if (tag) "tag" else "block", JsonPrimitive(id.toString()))
            mass?.let { d -> obj.add("mass", JsonPrimitive(d)) }
            friction?.let { d -> obj.add("friction", JsonPrimitive(d)) }
            elasticity?.let { d -> obj.add("elasticity", JsonPrimitive(d)) }
            priority?.let { d -> obj.add("priority", JsonPrimitive(d)) }
            return obj
        }
    }
}
