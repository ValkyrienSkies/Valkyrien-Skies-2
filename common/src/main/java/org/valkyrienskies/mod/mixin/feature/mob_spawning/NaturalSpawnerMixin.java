package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NaturalSpawner.SpawnState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.mob_spawning.ShipNaturalSpawner;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void vs$cancelVanillaInShipyardChunks(final ServerLevel level, final LevelChunk chunk,
        final SpawnState spawnState,
        final boolean spawnFriendlies, final boolean spawnMonsters, final boolean spawnPassive,
        final CallbackInfo ci) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunk.getPos().x, chunk.getPos().z)) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnForChunk", at = @At("TAIL"))
    private static void vs$spawnForShipsInChunk(final ServerLevel level, final LevelChunk chunk,
        final SpawnState spawnState,
        final boolean spawnFriendlies, final boolean spawnMonsters, final boolean spawnPassive,
        final CallbackInfo ci) {
        if (!VSGameConfig.SERVER.getAllowMobSpawns()) return;
        ShipNaturalSpawner.spawnForShipsIn(level, chunk, spawnState, spawnFriendlies, spawnMonsters, spawnPassive);
    }
}
