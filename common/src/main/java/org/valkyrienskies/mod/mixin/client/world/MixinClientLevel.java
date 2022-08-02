package org.valkyrienskies.mod.mixin.client.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipDataCommon;
import org.valkyrienskies.core.hooks.VSCoreHooksKt;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private Int2ObjectMap<Entity> entitiesById;

    @Inject(method = "disconnect", at = @At("TAIL"))
    private void afterDisconnect(final CallbackInfo ci) {
        VSCoreHooksKt.getCoreHooks().afterDisconnect();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        // Drag entities
        EntityDragger.INSTANCE.dragEntitiesWithShips(entitiesById.values());
        VSGameUtilsKt.getShipObjectWorld(minecraft).getNetworkManager()
            .tick(minecraft.getConnection().getConnection().getRemoteAddress());
    }

    @Inject(
        at = @At("HEAD"),
        method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
        cancellable = true
    )
    private void beforePlayLocalSound(final double x, final double y, final double z, final SoundEvent sound,
        final SoundSource category, final float volume, final float pitch, final boolean distanceDelay,
        final CallbackInfo ci) {

        final ShipDataCommon ship = VSGameUtilsKt.getShipManagingPos(ClientLevel.class.cast(this), x, y, z);
        if (ship != null) {
            final Vector3d p = ship.getShipToWorld().transformPosition(new Vector3d(x, y, z));
            playLocalSound(p.x, p.y, p.z, sound, category, volume, pitch, distanceDelay);

            ci.cancel();
        }
    }

    @Shadow
    public abstract void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category,
        float volume,
        float pitch, boolean bl);

}
