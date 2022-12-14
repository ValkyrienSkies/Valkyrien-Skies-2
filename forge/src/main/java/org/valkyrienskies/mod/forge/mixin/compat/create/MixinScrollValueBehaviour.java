package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.foundation.tileEntity.behaviour.scrollvalue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ScrollValueBehaviour.class)
public class MixinScrollValueBehaviour {

    @ModifyExpressionValue(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        ),
        method = "testHit"
    )
    private Vec3 transformHitPosToWorld(final Vec3 original) {
        return VSGameUtilsKt.toWorldCoordinates(Minecraft.getInstance().level, original);
    }

}
