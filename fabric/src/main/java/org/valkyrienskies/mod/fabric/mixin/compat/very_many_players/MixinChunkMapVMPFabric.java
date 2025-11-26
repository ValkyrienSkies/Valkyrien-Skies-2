package org.valkyrienskies.mod.fabric.mixin.compat.very_many_players;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.internal.world.VsiPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;

@Mixin(ChunkMap.class)
public class MixinChunkMapVMPFabric {

    @Shadow
    @Final
    ServerLevel level;

    @TargetHandler(
        mixin = "com.ishland.vmp.mixins.playerwatching.MixinThreadedAnvilChunkStorage",
        name = "method_17210"
    )
    @Inject(
        method = "@MixinSquared:handler", at = @At("RETURN"),
        cancellable = true
    )
    private void postGetPlayersWatchingChunk_0(final ChunkPos chunkPos, final boolean onlyOnWatchDistanceEdge,
        final CallbackInfoReturnable<List<ServerPlayer>> cir){
        vs$getPlayersWatchingChunk(chunkPos, cir);
    }

    @TargetHandler(
        mixin = "com.ishland.vmp.mixins.playerwatching.MixinThreadedAnvilChunkStorage",
        name = "method_37907"
    )
    @Inject(
        method = "@MixinSquared:handler", at = @At("RETURN"),
        cancellable = true
    )
    private void postGetPlayersWatchingChunk_1(ChunkPos chunkPos, final CallbackInfoReturnable<List<ServerPlayer>> cir){
        vs$getPlayersWatchingChunk(chunkPos, cir);
    }

    @Unique
    private void vs$getPlayersWatchingChunk(final ChunkPos chunkPos, final CallbackInfoReturnable<List<ServerPlayer>> cir){
        final Iterator<VsiPlayer> playersWatchingShipChunk =
            VSGameUtilsKt.getShipObjectWorld(level)
                .getIPlayersWatchingShipChunk(chunkPos.x, chunkPos.z, VSGameUtilsKt.getDimensionId(level));

        if (!playersWatchingShipChunk.hasNext()) {
            // No players watching this ship chunk, so we don't need to modify anything
            return;
        }

        final List<ServerPlayer> oldReturnValue = cir.getReturnValue();
        final Set<ServerPlayer> watchingPlayers = new HashSet<>(oldReturnValue);

        playersWatchingShipChunk.forEachRemaining(
            iPlayer -> {
                final MinecraftPlayer minecraftPlayer = (MinecraftPlayer) iPlayer;
                final ServerPlayer playerEntity =
                    (ServerPlayer) minecraftPlayer.getPlayerEntityReference().get();
                if (playerEntity != null) {
                    watchingPlayers.add(playerEntity);
                }
            }
        );

        cir.setReturnValue(new ArrayList<>(watchingPlayers));
    }

    @TargetHandler(
        mixin = "com.ishland.vmp.mixins.playerwatching.MixinThreadedAnvilChunkStorage",
        name = "method_38783"
    )
    @WrapMethod(
        method = "@MixinSquared:handler"
    )
    private boolean onHasPlayersNearby(ChunkPos chunkPos, Operation<Boolean> original){
        boolean result = original.call(chunkPos);
        if (result) return true;
        Iterator<VsiPlayer> vsPlayersWatching = VSGameUtilsKt.getShipObjectWorld(level).getIPlayersWatchingShipChunk(chunkPos.x, chunkPos.z, VSGameUtilsKt.getDimensionId(level));
        return vsPlayersWatching.hasNext();
    }
}
