package org.valkyrienskies.mod.mixin.mod_compat.sound_physics_remastered;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics",remap = false)
public abstract class MixinSoundPhysics {


    @Inject(
        at = @At("HEAD"),
        method = "processSound(IDDDLnet/minecraft/sounds/SoundSource;Ljava/lang/String;Z)Lnet/minecraft/world/phys/Vec3;",
        cancellable = true
    )
    private static void beforeProcessSound(int source, double posX, double posY, double posZ, SoundSource category,
        String sound, boolean auxOnly, CallbackInfoReturnable<Vec3> cir){

        final ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        if (ValkyrienSkies.getShipManagingBlock(level, posX, posY, posZ) instanceof final ClientShip ship) {
            final Vector3d inWorldPos = ship.getShipToWorld().transformPosition(new Vector3d(posX, posY, posZ));
            cir.setReturnValue(processSound(source, inWorldPos.x, inWorldPos.y, inWorldPos.z, category, sound, auxOnly));
            cir.cancel();
        }
    }


    @Shadow
    public static Vec3 processSound(int source, double posX, double posY, double posZ, SoundSource category, String sound, boolean auxOnly) {
        throw new AssertionError();
    }

}
