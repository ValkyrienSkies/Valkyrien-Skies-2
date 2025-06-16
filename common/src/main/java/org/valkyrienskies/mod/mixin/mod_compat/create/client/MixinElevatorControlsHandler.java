package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorControlsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ElevatorControlsHandler.class)
public class MixinElevatorControlsHandler {
    // Fixes raytracing contraptions on ships
    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB onScrollGetBoundingBox(final AbstractContraptionEntity instance, final Operation<AABB> getBoundingBox) {
        final ClientShip clientShip = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, instance.getAnchorVec().x, instance.getAnchorVec().y, instance.getAnchorVec().z);
        if (clientShip != null) {
            final AABB original = getBoundingBox.call(instance);
            final AABBdc modified = VectorConversionsMCKt.toJOML(original).transform(clientShip.getRenderTransform().getShipToWorld());
            return VectorConversionsMCKt.toMinecraft(modified);
        }
        return getBoundingBox.call(instance);
    }
}
