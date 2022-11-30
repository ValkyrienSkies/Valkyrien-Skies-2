package org.valkyrienskies.mod.mixin.feature.spawn_player_on_ship;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void teleport(double d, double e, double f, float g, float h);

    @Shadow
    @Final
    static Logger LOGGER;

    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
        ),
        method = "handleMovePlayer",
        cancellable = true
    )
    private void injectHandleMovePlayer(final ServerboundMovePlayerPacket packet, final CallbackInfo ci) {
        if (EntityShipCollisionUtils.isCollidingWithUnloadedShips(this.player)) {
            ci.cancel();
            LOGGER.warn("{} moved while colliding with unloaded ships!", this.player.getName().getString());
            this.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(),
                this.player.getXRot());
        }
    }

}
