package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = SafeBlockEntityRenderer.class, remap = false)
public abstract class MixinSafeBlockEntityRenderer {
    /**
     * Currently only has use in Belt Rendering. This injector fixes invisible belt items on ship.
     */
    @WrapMethod(
        method = "shouldCullItem"
    )
    private boolean cullIfOnShip(Vec3 itemPos, Level level, Operation<Boolean> original){
        ClientShip ship = VSClientGameUtils.getClientShip(itemPos.x, itemPos.y, itemPos.z);
        if (ship != null) {
            Vector3d shipItemPosD = ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(itemPos));
            return original.call(VectorConversionsMCKt.toMinecraft(shipItemPosD), level);
        } else return original.call(itemPos, level);
    }
}
