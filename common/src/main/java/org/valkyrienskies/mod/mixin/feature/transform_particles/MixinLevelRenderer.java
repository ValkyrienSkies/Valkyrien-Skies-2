package org.valkyrienskies.mod.mixin.feature.transform_particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.ParticleOptions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow
    private ClientLevel level;

    @Shadow
    @Nullable
    protected abstract Particle addParticleInternal(ParticleOptions parameters, boolean alwaysSpawn,
        boolean canSpawnOnMinimal,
        double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    /**
     * Render particles in-world. The {@link MixinParticle} is not sufficient because this method includes a distance
     * check, but this mixin is also not sufficient because not every particle is spawned using this method.
     */
    @Inject(
        method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spawnParticleInWorld(final ParticleOptions parameters, final boolean alwaysSpawn,
        final boolean canSpawnOnMinimal,
        final double x, final double y, final double z, final double velocityX, final double velocityY,
        final double velocityZ,
        final CallbackInfoReturnable<Particle> cir
    ) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, (int) x >> 4, (int) z >> 4);

        if (ship == null) {
            // vanilla behaviour
            return;
        }

        final Matrix4dc transform = ship.getRenderTransform().getShipToWorldMatrix();
        // in-world position
        final Vector3d p = transform.transformPosition(new Vector3d(x, y, z));

        // in-world velocity
        final Vector3d v = transform
            // Rotate velocity wrt ship transform
            .transformDirection(new Vector3d(velocityX, velocityY, velocityZ))
            // Tack on the ships linear velocity (multiplied by 1/20 because particle velocity is given per tick)
            .fma(0.05, ship.getShipData().getPhysicsData().getLinearVelocity());

        // Return and re-call this method with new coords
        cir.setReturnValue(
            addParticleInternal(parameters, alwaysSpawn, canSpawnOnMinimal, p.x, p.y, p.z, v.x, v.y, v.z));
    }

}
