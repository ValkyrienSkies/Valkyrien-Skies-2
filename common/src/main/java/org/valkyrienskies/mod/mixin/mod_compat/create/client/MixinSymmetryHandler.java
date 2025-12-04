package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(SymmetryHandler.class)
public class MixinSymmetryHandler {
    @WrapOperation(
        method = {"render", "onRenderWorld"},
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0)
    )
    private static void translateToWorldIfShip(PoseStack ms, double d, double e, double f,
        Operation<Void> original, @Local(name = "pos")
        BlockPos pos, @Local(name = "view") Vec3 view){
        ClientShip ship = VSClientGameUtils.getClientShip(pos.getX(), pos.getY(), pos.getZ());
        if(ship != null) {
            Vector3d posInWorld = ship.getRenderTransform().getShipToWorld().transformPosition(pos.getX(), pos.getY(), pos.getZ(), new Vector3d());
            ms.translate(posInWorld.x - view.x, posInWorld.y - view.y, posInWorld.z - view.z);
            ms.mulPose(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        } else original.call(ms, d, e, f);
    }
}
