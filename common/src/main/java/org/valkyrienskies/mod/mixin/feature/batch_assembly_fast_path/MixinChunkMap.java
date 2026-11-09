package org.valkyrienskies.mod.mixin.feature.batch_assembly_fast_path;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.assembly.BatchAssemblyFastPath;

@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void vs$skipDiskReadForBatchAssembly(
        final ChunkPos pos,
        final CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir
    ) {
        if (!BatchAssemblyFastPath.isActive()) return; // cheap static check, hot path
        if (!BatchAssemblyFastPath.isRegistered(pos.toLong())) return;

        cir.setReturnValue(CompletableFuture.completedFuture(new ProtoChunk(
            pos, UpgradeData.EMPTY, this.level,
            this.level.registryAccess().registryOrThrow(Registries.BIOME), null
        )));
    }

    // 1.21 generates chunks through GenerationChunkHolder.applyStep rather than
    // ChunkMap.schedule. MixinChunkStatus skips the shipyard worldgen stages there;
    // keep the vanilla generation-holder/light pipeline intact.
}
