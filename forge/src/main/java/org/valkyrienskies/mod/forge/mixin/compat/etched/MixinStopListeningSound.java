package org.valkyrienskies.mod.forge.mixin.compat.etched;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gg.moonflower.etched.api.sound.StopListeningSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Sorry Etched, I would have made a PR but I couldn't get VS working in your gradle because of an Architectury loom bug.
 * I've used ModifyExpressionValue here to hopefully make this compatible if other mods also mixin this... for some reason.
 * Also because @Overwrite scares me.
 */
@Mixin(StopListeningSound.class)
public class MixinStopListeningSound {
    @Final
    @Shadow
    private SoundInstance source;

    @ModifyExpressionValue(
            method = "getX",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getX()D")
    )
    private double getX(double original) {
        Vec3 position = new Vec3(source.getX(), source.getY(), source.getZ());
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 newPosition = VSGameUtilsKt.toWorldCoordinates(level, position);
        return newPosition.x;
    }

    @ModifyExpressionValue(
            method = "getY",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getY()D")
    )
    private double getY(double original) {
        Vec3 position = new Vec3(source.getX(), source.getY(), source.getZ());
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 newPosition = VSGameUtilsKt.toWorldCoordinates(level, position);
        return newPosition.y;
    }

    @ModifyExpressionValue(
            method = "getZ",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getZ()D")
    )
    private double getZ(double original) {
        Vec3 position = new Vec3(source.getX(), source.getY(), source.getZ());
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 newPosition = VSGameUtilsKt.toWorldCoordinates(level, position);
        return newPosition.z;
    }
}
