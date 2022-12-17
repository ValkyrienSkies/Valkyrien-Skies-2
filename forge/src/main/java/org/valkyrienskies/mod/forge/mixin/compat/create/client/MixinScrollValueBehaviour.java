package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.simibubi.create.foundation.tileEntity.behaviour.scrollvalue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ScrollValueBehaviour.class)
public class MixinScrollValueBehaviour {

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        method = "testHit"
    )
    private Vec3 transformHitToShip(final Vec3 hitPos, final Vec3 blockPos) {
        final Vec3 inShipHit = VSGameUtilsKt.toShipRenderCoordinates(Minecraft.getInstance().level, blockPos, hitPos);
        return inShipHit.subtract(blockPos);
    }

}
