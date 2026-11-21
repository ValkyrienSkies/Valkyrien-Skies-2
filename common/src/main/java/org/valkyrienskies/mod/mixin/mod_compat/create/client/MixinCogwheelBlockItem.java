package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(targets = {
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$SmallCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$LargeCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$DiagonalCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$IntegratedLargeCogHelper",
        "com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem$IntegratedSmallCogHelper"
})
public class MixinCogwheelBlockItem {
    @Redirect(method = "getOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetLocation(BlockHitResult instance) {
        return RaycastUtilsKt.blockHitForUse(Minecraft.getInstance().level, instance).getLocation();
    }
}
