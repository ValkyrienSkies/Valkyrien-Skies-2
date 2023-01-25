package org.valkyrienskies.mod.compat

import net.minecraft.world.entity.Entity

object CreateCompat {

    private val contraptionClass = runCatching {
        Class.forName("com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity")
    }.getOrNull()

    @JvmStatic
    fun isContraption(entity: Entity): Boolean {
        return contraptionClass?.isInstance(entity) ?: false
    }
}
