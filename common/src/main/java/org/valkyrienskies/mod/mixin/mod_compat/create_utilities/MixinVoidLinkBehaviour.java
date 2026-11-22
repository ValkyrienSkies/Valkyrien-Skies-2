package org.valkyrienskies.mod.mixin.mod_compat.create_utilities;

import me.duquee.createutilities.blocks.voidtypes.VoidLinkBehaviour;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.mod.common.util.ShipAwareHit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VoidLinkBehaviour.class)
public class MixinVoidLinkBehaviour {

    @Redirect(
            method = "testHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec) {
        return ShipAwareHit.subtract(((VoidLinkBehaviour) (Object) this).getWorld(), instance, vec);
    }

}
