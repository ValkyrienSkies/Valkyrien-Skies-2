package org.valkyrienskies.mod.mixin.server;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.valkyrienskies.mod.common.ShipEffectLocations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.util.KrunchSupport;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Shadow
    @Final
    private MinecraftServer server;

    private record Delivery(Packet<?> packet, Set<ServerGamePacketListenerImpl> recipients) {}
    @Unique
    private static final ThreadLocal<Delivery> vs$delivery = new ThreadLocal<>();

    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void afterPlayerJoin(final Connection netManager, final ServerPlayer player, final CallbackInfo ci) {
        final MinecraftPlayer wrapped = ValkyrienSkies.getPlayerWrapper(player);
        getVsCore().getHooks().afterClientJoinServer(wrapped);
        if (!KrunchSupport.INSTANCE.isKrunchSupported()) {
            player.sendSystemMessage(
                Component.literal(
                    "VS2 physics are disabled on this server, because Krunch is not supported on this server!" +
                        " Current supported platforms are:" +
                        " Windows x86-64, Linux x86-64, Linux aarch64, Macos x86-64, Macos arm64")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        VSEntityManager.INSTANCE.syncHandlers(wrapped);
    }

    @WrapMethod(method = "broadcast")
    private void vs$broadcast(@Nullable Player excluded, double x, double y, double z, double radius,
        ResourceKey<Level> dimension, Packet<?> packet, Operation<Void> original) {
        final var level = server.getLevel(dimension);
        final var ship = level == null ? null : VSGameUtilsKt.getLoadedShipManagingPos(level, BlockPos.containing(x, y, z));
        if (ship == null) {
            original.call(excluded, x, y, z, radius, dimension, packet);
            return;
        }
        final var pieces = VSGameUtilsKt.getAllShips(level).getSplitPieces(ship.getId());
        if (pieces.isEmpty()) {
            if (excluded != null && VSGameUtilsKt.isChunkInShipyard(level, excluded.chunkPosition().x, excluded.chunkPosition().z)) {
                original.call(excluded, x, y, z, radius, dimension, packet);
                return;
            }
            final var p = VSGameUtilsKt.toWorldCoordinates(ship, x, y, z);
            original.call(excluded, p.x, p.y, p.z, radius, dimension, packet);
            return;
        }
        final BlockPos cell = packet instanceof ClientboundBlockEventPacket event ? event.getPos() : BlockPos.containing(x, y, z);
        final var positions = ShipEffectLocations.block(pieces, cell, x, y, z);
        if (positions.size() == 1) {
            final var p = positions.get(0);
            original.call(excluded, p.x, p.y, p.z, radius, dimension, packet);
            return;
        }
        final var previous = vs$delivery.get();
        vs$delivery.set(new Delivery(packet, new HashSet<>()));
        try {
            for (var p : positions) original.call(excluded, p.x, p.y, p.z, radius, dimension, packet);
        } finally {
            if (previous == null) vs$delivery.remove();
            else vs$delivery.set(previous);
        }
    }

    @WrapOperation(method = "broadcast", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void vs$sendOnce(ServerGamePacketListenerImpl connection, Packet<?> outgoing, Operation<Void> original,
        @Local(argsOnly = true) Packet<?> broadcast) {
        final var delivery = vs$delivery.get();
        if (delivery == null || broadcast != delivery.packet() || delivery.recipients().add(connection)) {
            original.call(connection, outgoing);
        }
    }
}
