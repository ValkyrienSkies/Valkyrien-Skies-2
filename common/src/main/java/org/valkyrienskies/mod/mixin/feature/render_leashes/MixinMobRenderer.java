package org.valkyrienskies.mod.mixin.feature.render_leashes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(MobRenderer.class)
public class MixinMobRenderer {

    // For leashes rendering
    @WrapOperation(method = "renderLeash", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 getRopeHoldPosition(final Entity instance, final float partialTicks,
        final Operation<Vec3> getRopeHoldPosition) {
        final Vec3 origVec = getRopeHoldPosition.call(instance, partialTicks);
        final Vector3d vec = VectorConversionsMCKt.toJOML(origVec);

        final LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(instance.level(), vec);
        if (ship != null) {
            ship.getShipToWorld().transformPosition(vec);
            return VectorConversionsMCKt.toMinecraft(vec);
        } else {
            return origVec;
        }
    }

}
