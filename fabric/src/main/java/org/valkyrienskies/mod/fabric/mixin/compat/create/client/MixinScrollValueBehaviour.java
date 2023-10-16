package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ScrollValueBehaviour.class)
public class MixinScrollValueBehaviour {

//    @Redirect(
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
//            ),
//            method = "testHit"
//    )
//    private Vec3 transformHitToShip(final Vec3 hitPos, final Vec3 blockPos) {
//        final Vec3 inShipHit = VSGameUtilsKt.toShipRenderCoordinates(Minecraft.getInstance().level, blockPos, hitPos);
//        return inShipHit.subtract(blockPos);
//    }

}
