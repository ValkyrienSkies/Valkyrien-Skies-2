package org.valkyrienskies.mod.client

import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

class EmptyRenderer(dispatcher: EntityRenderDispatcher) :
    EntityRenderer<Entity>(dispatcher) {

    override fun getTextureLocation(entity: Entity): ResourceLocation? = null
}
