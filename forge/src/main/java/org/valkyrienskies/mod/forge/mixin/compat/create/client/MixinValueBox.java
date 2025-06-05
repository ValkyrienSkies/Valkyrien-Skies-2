package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ValueBox.class)
public class MixinValueBox {

    @Shadow
    protected BlockPos pos;

   @WrapOperation(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/createmod/catnip/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    public void wrapOpTranslate(final PoseStack instance, final double x, final double y, final double z, final Operation<Void> operation) {
        final ClientShip ship = VSClientGameUtils.getClientShip(x, y, z);
        if (ship != null) {
            final var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance, pos.getX(),pos.getY(),pos.getZ(), camera.x, camera.y, camera.z );
        } else {
            operation.call(instance, x, y, z);
        }
    }
}
