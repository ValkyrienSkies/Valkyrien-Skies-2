package org.valkyrienskies.mod.mixin.feature.sound.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundSoundPacket.class)
public abstract class MixinClientboundSoundPacket {
    @Shadow @Final private int x;
    @Shadow @Final private int y;
    @Shadow @Final private int z;

    @ModifyReturnValue(method = "getX", at = @At("RETURN"))
    private double vs$unpackX(double original) {
        return x / 8.0;
    }

    @ModifyReturnValue(method = "getY", at = @At("RETURN"))
    private double vs$unpackY(double original) {
        return y / 8.0;
    }

    @ModifyReturnValue(method = "getZ", at = @At("RETURN"))
    private double vs$unpackZ(double original) {
        return z / 8.0;
    }
}
