package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NaturalSpawner.SpawnState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void determineSpawningOnShips(final ServerLevel level, final LevelChunk chunk,
        final SpawnState spawnState,
        final boolean spawnFriendlies, final boolean spawnMonsters, final boolean bl, final CallbackInfo ci) {
        if (ValkyrienSkies.isChunkInShipyard(level, chunk.getPos().x, chunk.getPos().z)) {
            if (!VSGameConfig.SERVER.getAllowMobSpawns()) {
                ci.cancel();
            }
        }
    }
}
