package org.valkyrienskies.mod.forge.mixin.compat.mekanism;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.RenderResizableCuboid;
import mekanism.client.render.RenderResizableCuboid.FaceDisplay;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = RenderResizableCuboid.class, remap = false)
public class MixinRenderResizableCuboid {
    /**
     * Fixes model faces being incorrectly culled due to culling logic not accounting for ships.<p>
     *
     * Drawing a few extra quads should be faster than determining which exact ship our cuboid belongs to and then
     * transforming each face to world (or camera position to shipyard).
     * With Sodium we already disable block face culling optimizations altogether and a few fluid tanks surely have
     * less performance impact.<p>
     *
     * No reason to mix in so late as faceDisplay is passed way earlier in the call stack,
     * but a late injection point leaves us a possibility to quickly replace this stub with proper camera
     * coordinate transformation if this is deemed necessary.
     */
    @WrapMethod(
        method = "renderCube(Lmekanism/client/render/MekanismRenderer$Model3D;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;[IIILmekanism/client/render/RenderResizableCuboid$FaceDisplay;Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/Vec3;)V"
    )
    private static void transformIfOnShip(
        Model3D cube, PoseStack matrix, VertexConsumer buffer, int[] colors, int light, int overlay,
        FaceDisplay faceDisplay, Camera camera, Vec3 renderPos, Operation<Void> original
    ) {
        if (renderPos != null && VSGameUtilsKt.isBlockInShipyard(camera.getEntity().level(), renderPos)) {
            faceDisplay = FaceDisplay.BOTH;
        }
        original.call(cube, matrix, buffer, colors, light, overlay, faceDisplay, camera, renderPos);
    }
}
