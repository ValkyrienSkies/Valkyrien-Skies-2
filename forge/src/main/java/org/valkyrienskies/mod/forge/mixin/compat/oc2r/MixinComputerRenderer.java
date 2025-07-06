package org.valkyrienskies.mod.forge.mixin.compat.oc2r;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import li.cil.oc2.client.renderer.blockentity.ComputerRenderer;
import li.cil.oc2.common.blockentity.ComputerBlockEntity;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(ComputerRenderer.class)
public class MixinComputerRenderer {
    @WrapOperation(method = "render(Lli/cil/oc2/common/blockentity/ComputerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
    ))
    private Vec3 valkyrienskies$renderEyePositionTransform(Entity instance, float partialTicks, Operation<Vec3> original, @Local(argsOnly = true) ComputerBlockEntity be) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(be.getLevel(), be.getBlockPos());
        if (ship == null) return original.call(instance, partialTicks);

        return ValkyrienSkies.positionToShip(ship, original.call(instance, partialTicks));
    }

    @WrapOperation(method = "renderTerminal", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
    ))
    private boolean valkyrienskies$renderTerminalDistanceCheck(Vec3 instance, Position arg, double d, Operation<Boolean> original, @Local(argsOnly = true) ComputerBlockEntity be) {
        return ValkyrienSkies.closerThan(be.getLevel(), instance, arg, d);
    }

    @WrapOperation(method = "renderStatusText", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
    ))
    private boolean valkyrienskies$renderStatusTextDistanceCheck(Vec3 instance, Position arg, double d, Operation<Boolean> original, @Local(argsOnly = true) ComputerBlockEntity be) {
        return ValkyrienSkies.closerThan(be.getLevel(), instance, arg, d);
    }
}
