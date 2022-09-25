package org.valkyrienskies.mod.mixin.client.renderer.entity;

import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(MobRenderer.class)
public class MixinMobRenderer {

    // For leashes rendering
    @Redirect(method = "renderLeash", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;getRopeHoldPosition(F)Lnet/minecraft/world/phys/Vec3;"))
    public Vec3 getRopeHoldPosition(final Entity instance, final float partialTicks) {
        final Vec3 origVec = instance.getRopeHoldPosition(partialTicks);
        final Vector3d vec = VectorConversionsMCKt.toJOML(origVec);

        final ShipObject ship = VSGameUtilsKt.getShipObjectManagingPos(instance.level, vec);
        if (ship != null) {
            ship.getShipTransform().getShipToWorldMatrix().transformPosition(vec);
            return VectorConversionsMCKt.toMinecraft(vec);
        } else {
            return origVec;
        }
    }

}
