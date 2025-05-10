package org.valkyrienskies.mod.forge.mixin.compat.mffs;

import dev.su5ed.mffs.network.ClientPacketHandler;
import dev.su5ed.mffs.render.particle.BeamParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(ClientPacketHandler.class)
public class MixinClientPacketHandler {
    @Redirect(method = "handleDrawBeamPacket", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
    ))
    private static void valkyrienskies$handleBeamsOnShips(ClientLevel instance, ParticleOptions arg, double d, double e, double f, double g, double h, double i) {
        if (!(arg instanceof BeamParticleOptions beam)) {
            instance.addParticle(arg, d, e, f, g, h, i);
            return;
        }

        Vec3 target = beam.target();
        final Ship ship = ValkyrienSkies.getShipManagingBlock(instance, ((BeamParticleOptions) arg).target());
        if (ship != null) {
            target = ValkyrienSkies.positionToWorld(ship, target);
        }

        instance.addParticle(new BeamParticleOptions(target, beam.color(), beam.lifetime()), d, e, f, g, h, i);
    }
}
