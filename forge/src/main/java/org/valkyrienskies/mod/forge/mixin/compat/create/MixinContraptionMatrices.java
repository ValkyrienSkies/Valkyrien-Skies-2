package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.mojang.math.Matrix4f;
import com.simibubi.create.content.contraptions.components.structureMovement.render.ContraptionMatrices;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = ContraptionMatrices.class, remap = false)
public class MixinContraptionMatrices {

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/components/structureMovement/render/ContraptionMatrices;translateToEntity(Lcom/mojang/math/Matrix4f;Lnet/minecraft/world/entity/Entity;F)V"
        ),
        method = "setup"
    )
    private void redirectTranslateToEntity(final Matrix4f matrix, final Entity entity, final float partialTicks) {
        if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
            final double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
            final double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
            final double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());

            final Matrix4d worldMatrix = new Matrix4d()
                .mul(ship.getRenderTransform().getShipToWorld())
                .translate(x, y, z);

            VectorConversionsMCKt.set(matrix, worldMatrix);
        }
    }

}
