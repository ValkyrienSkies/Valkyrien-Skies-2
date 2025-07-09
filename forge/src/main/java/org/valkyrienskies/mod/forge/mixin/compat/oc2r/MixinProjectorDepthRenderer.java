package org.valkyrienskies.mod.forge.mixin.compat.oc2r;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc2.client.renderer.ProjectorDepthRenderer;
import li.cil.oc2.common.blockentity.ProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(ProjectorDepthRenderer.class)
public abstract class MixinProjectorDepthRenderer {

    @ModifyVariable(method = "renderProjectorDepths", at = @At("STORE"), ordinal = 1, remap = false)
    private static Vec3 valkyrienskies$transformProjectorPosToWorld(Vec3 original, @Local(argsOnly = true) ClientLevel level) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(level, original);
        if (ship == null) {
            return original;
        }

        return ValkyrienSkies.positionToWorld(ship, original);
    }

    @Inject(method = "renderProjectorDepths", at = @At(
        value = "INVOKE",
        target = "Lli/cil/oc2/client/renderer/ProjectorDepthRenderer;setupViewModelMatrix(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        shift = Shift.AFTER
    ), remap = false)
    private static void valkyrienskies$transformViewModelStack(Minecraft minecraft, ClientLevel level, float partialTicks, int projectorCount, CallbackInfo ci, @Local PoseStack stack, @Local ProjectorBlockEntity projector) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(level, projector.getBlockPos());
        if (ship != null) {
            stack.mulPose(ship.getTransform().getShipToWorldRotation().get(new Quaternionf()).invert());
        }
    }
}
