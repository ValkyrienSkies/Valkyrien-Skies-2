package org.valkyrienskies.mod.mixin.feature.render_pathfinding;

import io.netty.buffer.Unpooled;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder.PathAccessor;

@Mixin(DebugPackets.class)
public class MixinDebugPackets {

    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"))
    private static void sendPathFindingPacket(
        final Level level,
        final Mob mob,
        final Path path,
        final float maxDistanceToWaypoint,
        final CallbackInfo ci
    ) {
        if (path == null || level.isClientSide) {
            return;
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeInt(mob.getId());
        buf.writeFloat(maxDistanceToWaypoint);
        writePath(buf, path);

        final ClientboundCustomPayloadPacket lv =
            new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.DEBUG_PATHFINDING_PACKET, buf);
        for (final Player player : level.players()) {
            ((ServerPlayer) player).connection.send(lv);
        }
    }

    private static void writePath(final FriendlyByteBuf buffer, final Path path) {
        buffer.writeBoolean(path.canReach());
        buffer.writeInt(path.getNextNodeIndex());

        final Set<Target> targetSet = ((PathAccessor) path).getTargetNodes();
        if (targetSet != null) {
            buffer.writeInt(targetSet.size());

            targetSet.forEach((node) -> {
                writeNode(buffer, node);
            });
        } else {
            buffer.writeInt(0);
        }

        buffer.writeInt(path.getTarget().getX());
        buffer.writeInt(path.getTarget().getY());
        buffer.writeInt(path.getTarget().getZ());

        buffer.writeInt(path.getNodeCount());

        for (int i = 0; i < path.getNodeCount(); ++i) {
            final Node node = path.getNode(i);
            writeNode(buffer, node);
        }

        buffer.writeInt(0);
        /*
        buffer.writeInt(path.debugNodes.length);
        PathNode[] var6 = path.debugNodes;
        int var7 = var6.length;

        int var4;
        PathNode pathNode2;
        for (var4 = 0; var4 < var7; ++var4) {
            pathNode2 = var6[var4];
            pathNode2.toBuffer(buffer);
        }
         */

        buffer.writeInt(0);
        /*
        var6 = path.debugSecondNodes;
        var7 = var6.length;

        for (var4 = 0; var4 < var7; ++var4) {
            pathNode2 = var6[var4];
            pathNode2.toBuffer(buffer);
        }
        */
    }

    private static void writeNode(final FriendlyByteBuf buffer, final Node node) {
        buffer.writeInt(node.x);
        buffer.writeInt(node.y);
        buffer.writeInt(node.z);
        buffer.writeFloat(node.walkedDistance);
        buffer.writeFloat(node.costMalus);
        buffer.writeBoolean(node.closed);
        buffer.writeInt(node.type.ordinal());
        buffer.writeFloat(node.f);
    }
}
