package org.valkyrienskies.mod.forge.mixin.feature.fix_tp_ships_cross_dimension;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class MixinLevelChunk extends ChunkAccess {

    public MixinLevelChunk(ChunkPos arg, UpgradeData arg2, LevelHeightAccessor arg3, Registry<Biome> arg4, long l, @Nullable LevelChunkSection[] args, @Nullable BlendingData arg5) {
        super(arg, arg2, arg3, arg4, l, args, arg5);
    }

    @Final
    @Shadow
    private Map<BlockPos, LevelChunk.RebindableTickingBlockEntityWrapper> tickersInLevel;

    @Shadow
    @Final
    private static TickingBlockEntity NULL_TICKER;

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"), cancellable = true)
    public void clearAllBlockEntities(CallbackInfo ci) {
        if (!this.blockEntities.isEmpty()) {
            List<BlockEntity> blockEntitiesSnapshot = List.copyOf(this.blockEntities.values());

            for (BlockEntity be : blockEntitiesSnapshot) {
                be.onChunkUnloaded();
            }

            for (BlockEntity be : blockEntitiesSnapshot) {
                be.setRemoved();
            }

            this.blockEntities.clear();
        }

        if (!this.tickersInLevel.isEmpty()) {
            List<LevelChunk.RebindableTickingBlockEntityWrapper> tickerSnapshot =
                List.copyOf(this.tickersInLevel.values());

            for (LevelChunk.RebindableTickingBlockEntityWrapper ticker : tickerSnapshot) {
                ticker.rebind(NULL_TICKER);
            }

            this.tickersInLevel.clear();
        }
        ci.cancel();
    }

}
