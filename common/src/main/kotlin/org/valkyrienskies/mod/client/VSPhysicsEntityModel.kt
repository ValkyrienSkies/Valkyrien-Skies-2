// Made with Blockbench 4.12.3
// Exported for Minecraft version 1.17 or later with Mojang mappings
package org.valkyrienskies.mod.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Mob
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

class VSPhysicsEntityModel<T : Mob>(root: ModelPart) :
    EntityModel<T>() {
    private val bb_main: ModelPart = root.getChild("bb_main")

    override fun renderToBuffer(
        poseStack: PoseStack, vertexConsumer: VertexConsumer, packedLight: Int, packedOverlay: Int, red: Float,
        green: Float, blue: Float, alpha: Float
    ) {
        bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha)
    }

    override fun setupAnim(entity: T, f: Float, g: Float, h: Float, i: Float, j: Float) {
    }

    companion object {
        // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
        val LAYER_LOCATION: ModelLayerLocation =
            ModelLayerLocation(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "test_sphere"), "main")

        fun createBodyLayer(): LayerDefinition {
            val meshdefinition = MeshDefinition()
            val partdefinition = meshdefinition.root

            val bb_main = partdefinition.addOrReplaceChild(
                "bb_main", CubeListBuilder.create().texOffs(0, 3)
                    .addBox(-4.0f, -4.0f, -4.0f, 8.0f, 2.0f, 8.0f, CubeDeformation(0.0f))
                    .texOffs(1, 1).addBox(-4.0f, -12.0f, 4.0f, 8.0f, 8.0f, 2.0f, CubeDeformation(0.0f))
                    .texOffs(2, 0).addBox(-2.0f, -10.0f, 6.0f, 4.0f, 4.0f, 2.0f, CubeDeformation(0.0f))
                    .texOffs(0, 3).addBox(-4.0f, -14.0f, -4.0f, 8.0f, 2.0f, 8.0f, CubeDeformation(0.0f))
                    .texOffs(2, 0).addBox(-2.0f, -10.0f, -8.0f, 4.0f, 4.0f, 2.0f, CubeDeformation(0.0f))
                    .texOffs(0, 0).addBox(-2.0f, -16.0f, -2.0f, 4.0f, 2.0f, 4.0f, CubeDeformation(0.0f))
                    .texOffs(0, 0).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 2.0f, 4.0f, CubeDeformation(0.0f)),
                PartPose.offset(0.0f, 24.0f, 0.0f)
            )

            val cube_r1 = bb_main.addOrReplaceChild(
                "cube_r1", CubeListBuilder.create().texOffs(2, 0)
                    .addBox(-2.0f, -3.0f, 0.0f, 4.0f, 4.0f, 2.0f, CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(8.0f, -7.0f, 0.0f, 0.0f, -1.5708f, 0.0f)
            )

            val cube_r2 = bb_main.addOrReplaceChild(
                "cube_r2", CubeListBuilder.create().texOffs(2, 0)
                    .addBox(-2.0f, -3.0f, 0.0f, 4.0f, 4.0f, 2.0f, CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-6.0f, -7.0f, 0.0f, 0.0f, -1.5708f, 0.0f)
            )

            val cube_r3 = bb_main.addOrReplaceChild(
                "cube_r3", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-4.0f, -8.0f, 4.0f, 8.0f, 8.0f, 2.0f, CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(-10.0f, -4.0f, 0.0f, 0.0f, 1.5708f, 0.0f)
            )

            val cube_r4 = bb_main.addOrReplaceChild(
                "cube_r4", CubeListBuilder.create().texOffs(0, 3)
                    .addBox(-4.0f, -8.0f, 4.0f, 8.0f, 8.0f, 2.0f, CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -4.0f, 0.0f, 0.0f, 3.1416f, 0.0f)
            )

            val cube_r5 = bb_main.addOrReplaceChild(
                "cube_r5", CubeListBuilder.create().texOffs(0, 1)
                    .addBox(-4.0f, -8.0f, 4.0f, 8.0f, 8.0f, 2.0f, CubeDeformation(0.0f)),
                PartPose.offsetAndRotation(0.0f, -4.0f, 0.0f, 0.0f, 1.5708f, 0.0f)
            )

            return LayerDefinition.create(meshdefinition, 16, 16)
        }
    }
}
