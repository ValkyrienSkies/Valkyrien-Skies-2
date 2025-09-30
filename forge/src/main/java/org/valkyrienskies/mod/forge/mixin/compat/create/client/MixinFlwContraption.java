package org.valkyrienskies.mod.forge.mixin.compat.create.client;


import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.contraptions.render.ContraptionRenderInfo;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContraptionMatrices.class)
public abstract class MixinFlwContraption {


    @Inject(at = @At("HEAD"), method = "translateToEntity", cancellable = true, remap = false)
    private static void beforeSetupModelViewPartial(Matrix4f matrix, Entity entity, float pt, CallbackInfo ci) {

        if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(),
                matrix,
                Mth.lerp(pt, entity.xOld, entity.getX()),
                Mth.lerp(pt, entity.yOld, entity.getY()),
                Mth.lerp(pt, entity.zOld, entity.getZ()),
                // TODO: make sure this fix worked
                matrix.get(3,0),
                matrix.get(3,1),
                matrix.get(3,2)
            );

            ci.cancel();
        }
    }


    // TODO: Find where this moved
    /*@Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;move(DDD)Lnet/minecraft/world/phys/AABB;"
        ),
        method = "beginFrame"
    )
    private AABB transformLightboxToWorld(final AABB aabb, final double negCamX, final double negCamY,
        final double negCamZ) {
        return VSGameUtilsKt.transformAabbToWorld(this.contraption.entity.level(), aabb).move(negCamX, negCamY, negCamZ);
    }*/
}

