package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.jozufozu.flywheel.core.virtual.VirtualRenderWorld;
import com.jozufozu.flywheel.event.RenderLayerEvent;
import com.mojang.math.Matrix4f;
import com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.components.structureMovement.Contraption;
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionRenderInfo;
import com.simibubi.create.content.contraptions.components.structureMovement.render.FlwContraption;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = FlwContraption.class, remap = false)
public class MixinFlwContraption extends ContraptionRenderInfo {

    public MixinFlwContraption(
        final Contraption contraption,
        final VirtualRenderWorld renderWorld) {
        super(contraption, renderWorld);
    }

    @Inject(at = @At("HEAD"), method = "setupModelViewPartial", cancellable = true)
    private static void beforeSetupModelViewPartial(final Matrix4f matrix, final Matrix4f modelMatrix,
        final AbstractContraptionEntity entity, final double camX, final double camY, final double camZ, final float pt,
        final CallbackInfo ci) {

        if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(),
                matrix,
                Mth.lerp(pt, entity.xOld, entity.getX()),
                Mth.lerp(pt, entity.yOld, entity.getY()),
                Mth.lerp(pt, entity.zOld, entity.getZ()),
                camX,
                camY,
                camZ
            );

            matrix.multiply(modelMatrix);
            ci.cancel();
        }

    }

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/components/structureMovement/render/ContraptionMatrices;transform(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/PoseStack;)V"
        ),
        method = "renderInstanceLayer"
    )
    private void injectRenderInstanceLayer(final RenderLayerEvent event, final CallbackInfo ci) {
        final var entity = this.contraption.entity;
        if (VSGameUtilsKt.getShipManagingPos(entity.level, entity.position()) instanceof final ClientShip ship) {
            event.stack.popPose();
            event.stack.pushPose();
            VSClientGameUtils.transformRenderWithShip(
                ship.getRenderTransform(),
                event.stack,
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.xOld, entity.getX()),
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.yOld, entity.getY()),
                Mth.lerp(AnimationTickHolder.getPartialTicks(), entity.zOld, entity.getZ()),
                event.camX,
                event.camY,
                event.camZ
            );
        }
    }
}
