package org.valkyrienskies.mod.mixin.mod_compat.sound_physics_remastered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class MixinSoundPhysics {

    @Shadow
    public static void onPlaySound(final double posX, final double posY, final double posZ, final int sourceID) {
        throw new AssertionError();
    }

    @Inject(
        at = @At("HEAD"),
        method = "onPlaySound",
        cancellable = true
    )
    private static void beforeOnPlaySound(
        final double posX, final double posY, final double posZ, final int sourceID, final CallbackInfo ci) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        if (VSGameUtilsKt.getShipManagingPos(level, posX, posY, posZ) instanceof final ClientShip ship) {
            final Vector3d inWorldPos = ship.getShipToWorld().transformPosition(new Vector3d(posX, posY, posZ));
            onPlaySound(inWorldPos.x, inWorldPos.y, inWorldPos.z, sourceID);
            ci.cancel();
        }
    }

}
