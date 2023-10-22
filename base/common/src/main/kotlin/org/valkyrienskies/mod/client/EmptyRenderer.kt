package org.valkyrienskies.mod.client

import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity

class EmptyRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<Entity>(context) {

    override fun getTextureLocation(entity: Entity): ResourceLocation? = null
}
