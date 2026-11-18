package org.valkyrienskies.mod.client.render

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import org.valkyrienskies.mod.api.render.ShipMaterial

internal object ShipMaterials {
    private val include = ResourceLocation("shaders/include/vs_ship_materials.glsl")
    private val materials = LinkedHashMap<ResourceLocation, ShipMaterial>()
    private var loadingStarted = false

    @Synchronized
    fun register(id: ResourceLocation): ShipMaterial {
        materials[id]?.let { return it }
        check(!loadingStarted) { "Register ship material $id during client initialization, before shader loading" }
        return ShipMaterial(id, materials.size + 1).also { materials[id] = it }
    }

    @JvmStatic
    @Synchronized
    fun shaderResources(resources: ResourceProvider): ResourceProvider {
        loadingStarted = true
        if (materials.isEmpty()) return resources
        val effects = materials.values.toList()
        return ResourceProvider { id ->
            val original = resources.getResource(id)
            if (id != include) original else original.map { base ->
                Resource(base.source()) { materialSource(resources, base, effects).byteInputStream() }
            }
        }
    }

    private fun materialSource(resources: ResourceProvider, base: Resource, effects: List<ShipMaterial>) = buildString {
        appendLine("#define VS_SHIP_MATERIALS")
        appendLine("void vs_applyRegisteredMaterial(int id, vec3 shipPos, vec4 params, inout vec4 color) {")
        for (effect in effects) {
            val path = ResourceLocation(effect.id.namespace, "shaders/include/ship_material/${effect.id.path}.glsl")
            appendLine("// ${effect.id}")
            appendLine("if (id == ${effect.index}) {")
            resources.openAsReader(path).use { appendLine(it.readText()) }
            appendLine("}")
        }
        appendLine("}")
        base.openAsReader().use { append(it.readText()) }
    }
}
