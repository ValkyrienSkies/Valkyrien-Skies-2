package org.valkyrienskies.mod.forge.mixin.compat.tis3d;

import li.cil.tis3d.client.renderer.block.entity.CasingBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(CasingBlockEntityRenderer.class)
public abstract class MixinCasingTileEntityRender {
    @ModifyVariable(remap = false, method = "isBackFace",
        at = @At("STORE"), ordinal = 0)
    private Vec3 vs$isBackFace(final Vec3 original, final BlockPos position) {
        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, position);
        if (ship != null) {
            final Vector3d v3 = new Vector3d(original.x, original.y, original.z);
            final Vector3d shipyard_camera = ship.getTransform().getWorldToShip().transformPosition(v3);
            return new Vec3(shipyard_camera.x, shipyard_camera.y, shipyard_camera.z);
        } else {
            return original;
        }
    }
}

