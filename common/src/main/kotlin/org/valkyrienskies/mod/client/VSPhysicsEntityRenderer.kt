package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.client.VSPhysicsEntityModel.Companion.LAYER_LOCATION
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity

class VSPhysicsEntityRenderer<T : VSPhysicsEntity>(context: Context) :
    MobRenderer<T, VSPhysicsEntityModel<T>>(
        context,
        // Bake the layer
        VSPhysicsEntityModel(context.bakeLayer(LAYER_LOCATION)),
        // Shadow size
        0.0F
    ) {

    override fun render(
        mob: T, f: Float, g: Float, poseStack: PoseStack, multiBufferSource: MultiBufferSource, i: Int
    ) {

        val renderTransform = mob.getRenderTransform(
            ((Minecraft.getInstance() as IShipObjectWorldClientProvider).shipObjectWorld as VsiClientShipWorld)
        ) ?: return

        // Rotate with the ship
        poseStack.mulPose(Quaternionf(renderTransform.shipToWorldRotation))

        // Offset model from hitbox
        poseStack.translate(0.0, -0.5, 0.0)

        super.render(mob, f, g, poseStack, multiBufferSource, i)
    }

    override fun getTextureLocation(entity: T): ResourceLocation {
        return ResourceLocation(ValkyrienSkiesMod.MOD_ID, "textures/test_sphere.png")
    }


}
