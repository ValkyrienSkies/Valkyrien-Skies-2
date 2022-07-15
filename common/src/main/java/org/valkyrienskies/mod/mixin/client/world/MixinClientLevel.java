package org.valkyrienskies.mod.mixin.client.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.net.SocketAddress;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.networking.VSNetworking;
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

    @Unique
    private boolean serverNoUdp = false;
    @Unique
    private int tryConnectIn = 100;

    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        // Drag entities
        EntityDragger.Companion.dragEntitiesWithShips(entitiesById.values());
        if (!VSNetworking.INSTANCE.getUsesUDP() && !serverNoUdp) {
            tryConnectIn--;
            if (tryConnectIn <= 0) {
                final SocketAddress address = this.minecraft.getConnection().getConnection().getRemoteAddress();
                VSNetworking.INSTANCE.tryUdpClient(address, (boolean supports) -> {
                    if (!supports) {
                        serverNoUdp = true;
                    }
                });
            }
        }
    }

    @Inject(
        method = "playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlaySound(final Player player, final double x, final double y, final double z,
        final SoundEvent sound, final SoundSource category,
        final float volume, final float pitch, final CallbackInfo ci) {
        final ClientLevel self = ClientLevel.class.cast(this);
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(self, (int) x >> 4, (int) z >> 4);
        if (shipObject != null) {
            // in-world position
            final Vector3d p = shipObject.getRenderTransform()
                .getShipToWorldMatrix().transformPosition(new Vector3d(x, y, z));

            playLocalSound(p.x, p.y, p.z, sound, category, volume, pitch, false);
            ci.cancel();
        }
    }

    @Shadow
    public abstract void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category,
        float volume,
        float pitch, boolean bl);

}
