package org.valkyrienskies.mod.client.render

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import org.valkyrienskies.mod.api.render.ShipMaterial
import org.valkyrienskies.mod.api.render.ShipMaterialPass
import org.valkyrienskies.mod.api.render.ShipMaterialSelection

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

    @JvmStatic
    fun terrainFragment(resources: ResourceProvider, source: String): String =
        shaderResources(resources).openAsReader(include).use {
            source.replace("#import <minecraft:include/vs_ship_materials.glsl>", it.readText())
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

internal class ShipMaterialStack(val table: ShipMaterialTable, val offset: Int, val count: Int, val blended: Boolean)

internal class ShipMaterialTable {
    var texels = FloatArray(0)
        private set
    private var used = 0

    fun append(selections: List<ShipMaterialSelection>): ShipMaterialStack {
        val start = used
        val end = start + selections.size * 8
        if (texels.size < end) texels = texels.copyOf(maxOf(end, texels.size * 2))
        var blended = false
        for (selection in selections) {
            texels[used] = selection.material.index.toFloat()
            texels[used + 4] = selection.x
            texels[used + 5] = selection.y
            texels[used + 6] = selection.z
            texels[used + 7] = selection.w
            used += 8
            if (selection.pass == ShipMaterialPass.BLENDED) blended = true
        }
        return ShipMaterialStack(this, start / 4, selections.size, blended)
    }
}
