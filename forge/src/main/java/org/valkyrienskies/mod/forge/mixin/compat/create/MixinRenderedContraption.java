package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.mojang.math.Matrix4f;
import com.simibubi.create.content.contraptions.components.structureMovement.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.components.structureMovement.Contraption;
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionRenderInfo;
import com.simibubi.create.content.contraptions.components.structureMovement.render.RenderedContraption;
import com.simibubi.create.foundation.utility.worldWrappers.PlacementSimulationWorld;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ClientShip;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(RenderedContraption.class)
public class MixinRenderedContraption extends ContraptionRenderInfo {

    public MixinRenderedContraption(
        final Contraption contraption,
        final PlacementSimulationWorld renderWorld) {
        super(contraption, renderWorld);
    }

    @Inject(at = @At("HEAD"), method = "setupModelViewPartial", cancellable = true)
    private static void beforeSetupModelViewPartial(final Matrix4f matrix, final Matrix4f modelMatrix,
        final AbstractContraptionEntity entity, final double camX, final double camY, final double camZ, final float pt,
        final CallbackInfo ci) {

        final Ship nullableShip = VSGameUtilsKt.getShipManaging(entity);
        if (nullableShip instanceof ClientShip) {
            final ClientShip ship = (ClientShip) nullableShip;

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

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;move(DDD)Lnet/minecraft/world/phys/AABB;"
        ),
        method = "beginFrame"
    )
    private AABB transformLightboxToWorld(final AABB aabb, final double negCamX, final double negCamY,
        final double negCamZ) {
        return VSGameUtilsKt.transformAabbToWorld(this.contraption.entity.level, aabb).move(negCamX, negCamY, negCamZ);
    }
}
