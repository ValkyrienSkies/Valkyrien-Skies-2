package org.valkyrienskies.mod.mixin.server.network;

import java.util.Collections;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipData;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {

    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void send(Packet<?> arg);

    @Shadow
    private int awaitingTeleport;

    @Shadow
    private int tickCount;

    @Shadow
    private Vec3 awaitingPositionFromClient;

    @Shadow
    private int awaitingTeleportTime;

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"
        )
    )
    public double includeShipsInBlockInteractDistanceCheck(
        final ServerPlayer receiver, final double x, final double y, final double z) {
        if (VSConfig.getEnableInteractDistanceChecks()) {
            return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
        } else {
            return 0;
        }
    }

    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;useItemOn(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
        )
    )
    private InteractionResult wrapInteractBlock(final ServerPlayerGameMode receiver,
        final ServerPlayer serverPlayerEntity, final Level world, final ItemStack item,
        final InteractionHand hand, final BlockHitResult hitResult) {

        return PlayerUtil.INSTANCE.transformPlayerTemporarily(player,
            VSGameUtilsKt.getShipObjectManagingPos(world, hitResult.getBlockPos()),
            () -> receiver.useItemOn(player, world, item, hand, hitResult));
    }

    // Fixes:
    // https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/87
    // Bed Bug
    @Inject(
        method = "teleport(DDDFFLjava/util/Set;)V",
        at = @At(value = "HEAD"),
        cancellable = true
    )
    private void transformTeleport(final double x, final double y, final double z, final float yaw, final float pitch,
        final Set<ClientboundPlayerPositionPacket.RelativeArgument> relativeSet, final CallbackInfo ci) {

        final BlockPos blockPos = new BlockPos(x, y, z);
        final ShipData ship;

        // TODO add flag to disable this https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/30
        if ((ship = VSGameUtilsKt.getShipManagingPos((ServerLevel) player.level, blockPos)) != null) {
            final Vector3d pos = new Vector3d(x, y, z);
            ship.getShipToWorld().transformPosition(pos);

            this.awaitingPositionFromClient = VectorConversionsMCKt.toMinecraft(pos);
            if (++this.awaitingTeleport == Integer.MAX_VALUE) {
                this.awaitingTeleport = 0;
            }
            this.awaitingTeleportTime = this.tickCount;
            this.player.absMoveTo(pos.x, pos.y, pos.z, yaw, pitch);

            this.send(
                new ClientboundPlayerPositionPacket(pos.x, pos.y, pos.z, yaw, pitch, Collections.emptySet(),
                    awaitingTeleport));
            ci.cancel();
        }
    }

}
