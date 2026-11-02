package org.valkyrienskies.mod.mixin.feature.batch_assembly_fast_path;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
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
        final CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir
    ) {
        if (!BatchAssemblyFastPath.isActive()) return; // cheap static check, hot path
        if (!BatchAssemblyFastPath.isRegistered(pos.toLong())) return;

        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(new ProtoChunk(
            pos, UpgradeData.EMPTY, this.level,
            this.level.registryAccess().registryOrThrow(Registries.BIOME), null
        ))));
    }

    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void vs$skipSubFullStatusesForBatchAssembly(
        final ChunkHolder holder, final ChunkStatus status,
        final CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir
    ) {
        if (!BatchAssemblyFastPath.isActive()) return; // cheap static check, hot path
        if (status == ChunkStatus.EMPTY || status.isOrAfter(ChunkStatus.FULL)) return;
        if (!BatchAssemblyFastPath.isRegistered(holder.getPos().toLong())) return;

        final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> parentEither =
            holder.getOrScheduleFuture(status.getParent(), (ChunkMap) (Object) this).getNow(null);
        final ChunkAccess chunk = parentEither == null ? null : parentEither.left().orElse(null);
        if (chunk == null) {
            return; // fail soft: let the vanilla pipeline deal with it
        }
        if (chunk instanceof ProtoChunk && !chunk.getStatus().isOrAfter(status)) {
            ((ProtoChunk) chunk).setStatus(status);
        }
        cir.setReturnValue(CompletableFuture.completedFuture(Either.left(chunk)));
    }
}
