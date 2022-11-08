package org.valkyrienskies.mod.mixin.server;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.hooks.VSCoreHooksKt;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.util.KrunchSupport;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {

    @Shadow
    @Final
    private List<ServerPlayer> players;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    public abstract void broadcast(@Nullable Player player, double x, double y, double z, double distance,
        ResourceKey<Level> worldKey, Packet<?> packet);

    @Inject(
        method = "placeNewPlayer",
        at = @At("TAIL")
    )
    private void afterPlayerJoin(final Connection netManager, final ServerPlayer player, final CallbackInfo ci) {
        final MinecraftPlayer wrapped = VSGameUtilsKt.getPlayerWrapper(player);
        VSCoreHooksKt.getCoreHooks().afterClientJoinServer(wrapped);
        if (!KrunchSupport.INSTANCE.isKrunchSupported()) {
            player.sendMessage(
                new TextComponent(
                    "VS 2 physics are disabled on this server, because Krunch is not supported on this server! Currently only x86-64 Windows and Linux platforms are supported.").withStyle(
                    ChatFormatting.RED, ChatFormatting.BOLD),
                null);
        }
        VSEntityManager.INSTANCE.syncHandlers(wrapped);
    }

    /**
     * Transform x, y, z in sendToAround if they are in ship space.
     */
    @Inject(
        method = "broadcast",
        at = @At("HEAD"),
        cancellable = true
    )
    private void sendToAround(@Nullable final Player player, final double x, final double y, final double z,
        final double distance, final ResourceKey<Level> worldKey, final Packet<?> packet, final CallbackInfo ci) {

        // If something has transformed the player to the shipyard, don't transform
        if (player != null
            && ChunkAllocator.isChunkInShipyard(
            (int) player.position().x >> 4,
            (int) player.position().y >> 4)
        ) {
            return;
        }

        final Level world = server.getLevel(worldKey);
        if (world == null) {
            return;
        }

        final ShipObject ship = VSGameUtilsKt.getShipObjectManagingPos(world, (int) x >> 4, (int) z >> 4);
        if (ship == null) {
            return;
        }

        // position in-world
        final Vector3d p = VSGameUtilsKt.toWorldCoordinates(ship.getShipData(), x, y, z);

        // re-call with correct x, y, z and return
        broadcast(player, p.x, p.y, p.z, distance, worldKey, packet);
        ci.cancel();
    }

}
