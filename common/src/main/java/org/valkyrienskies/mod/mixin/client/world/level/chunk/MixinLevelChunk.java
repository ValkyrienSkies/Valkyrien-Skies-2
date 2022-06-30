package org.valkyrienskies.mod.mixin.client.world.level.chunk;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.mixinducks.client.LevelChunkDuck;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk implements LevelChunkDuck {

    @Shadow
    @Final
    private ChunkPos chunkPos;

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Unique
    private final Object2IntMap<BlockPos> lights = new Object2IntOpenHashMap<>();

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/ChunkBiomeContainer;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/TickList;Lnet/minecraft/world/level/TickList;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Ljava/util/function/Consumer;)V",
        at = @At("TAIL")
    )
    public void postConstruct(final Level level, final ChunkPos chunkPos, final ChunkBiomeContainer chunkBiomeContainer,
        final UpgradeData upgradeData, final TickList<Block> tickList, final TickList<Fluid> tickList2, final long l,
        final LevelChunkSection[] levelChunkSections, final Consumer<LevelChunk> consumer, final CallbackInfo ci) {

        final Iterable<BlockPos> allBlocks =
            BlockPos.betweenClosed(this.chunkPos.getMinBlockX(), 0, this.chunkPos.getMinBlockZ(),
                this.chunkPos.getMaxBlockX(), 255, this.chunkPos.getMaxBlockZ());
        for (final BlockPos pos : allBlocks) {
            final BlockState state = getBlockState(pos);
            if (state.getLightEmission() > 0) {
                lights.put(pos.immutable(), state.getLightEmission());
            }
        }

    }

    @Inject(method = "setBlockState", at = @At("TAIL"))
    public void postSetBlockState(final BlockPos pos, final BlockState state, final boolean moved,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState prevState = cir.getReturnValue();

        if (prevState.getLightEmission() > 0 && state.getLightEmission() == 0) {
            lights.removeInt(pos);
        } else if (state.getLightEmission() != prevState.getLightEmission()) {
            lights.put(pos, state.getLightEmission());
        }
    }

    @Override
    public Object2IntMap<BlockPos> vs$getLights() {
        return lights;
    }
}
