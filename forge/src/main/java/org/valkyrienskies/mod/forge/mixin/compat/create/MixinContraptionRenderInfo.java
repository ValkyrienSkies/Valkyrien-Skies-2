package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.components.structureMovement.Contraption;
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionRenderInfo;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ContraptionRenderInfo.class, remap = false)
public class MixinContraptionRenderInfo {

    @Shadow
    @Final
    public Contraption contraption;

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/components/structureMovement/AbstractContraptionEntity;getBoundingBoxForCulling()Lnet/minecraft/world/phys/AABB;"
        ),
        method = "beginFrame"
    )
    private AABB redirectGetAABBForCulling(final AbstractContraptionEntity receiver) {
        return VSGameUtilsKt.transformRenderAABBToWorld(((ClientLevel) receiver.level), receiver.position(),
            receiver.getBoundingBoxForCulling());
    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/components/structureMovement/render/ContraptionMatrices;setup(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/simibubi/create/content/contraptions/components/structureMovement/AbstractContraptionEntity;)V"
        ),
        method = "setupMatrices"
    )
    private void injectSetupMatrices(final PoseStack viewProjection, final double camX, final double camY,
        final double camZ, final CallbackInfo ci) {

        final var entity = this.contraption.entity;
        if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
            viewProjection.popPose();
            viewProjection.pushPose();
            VSClientGameUtils.transformRenderWithShip(
                ship.getRenderTransform(),
                viewProjection,
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.xOld, entity.getX()),
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.yOld, entity.getY()),
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.zOld, entity.getZ()),
                camX,
                camY,
                camZ
            );
        }
    }
}

