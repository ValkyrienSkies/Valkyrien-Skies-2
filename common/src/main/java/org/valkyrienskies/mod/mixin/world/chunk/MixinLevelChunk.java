package org.valkyrienskies.mod.mixin.world.chunk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VSLevelChunk;
import org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets.LevelChunkDuck;
import org.valkyrienskies.mod.util.FluidStateManager;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk extends ChunkAccess implements VSLevelChunk, LevelChunkDuck {
    @Unique
    private static final Set<Heightmap.Types> ALL_HEIGHT_MAP_TYPES = new HashSet<>(Arrays.asList((Heightmap.Types.values())));

    @Shadow
    @Final
    Level level;

    @Shadow
    @Mutable
    private LevelChunkTicks<Block> blockTicks;
    @Shadow
    @Mutable
    private LevelChunkTicks<Fluid> fluidTicks;

    @Unique
    private FluidStateManager.ChunkFluidData fluidData;

    // Dummy constructor
    public MixinLevelChunk(final Ship ship) {
        super(null, null, null, null, 0, null, null);
        throw new IllegalStateException("This should never be called!");
    }

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/ticks/LevelChunkTicks;Lnet/minecraft/world/ticks/LevelChunkTicks;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V",
        at = @At("RETURN")
    )
    private void LevelChunk$init(
        final Level level,
        final ChunkPos chunkPos,
        final UpgradeData data,
        final LevelChunkTicks<Block> blockTicks,
        final LevelChunkTicks<Fluid> fluidTicks,
        final long inhabitedTime,
        final LevelChunkSection[] sections,
        final LevelChunk.PostLoadProcessor postProcessor,
        final BlendingData blendingData,
        final CallbackInfo ci
    ) {
        this.fluidData = new FluidStateManager.ChunkFluidData();

        final BlockPos.MutableBlockPos tmpPos = new BlockPos.MutableBlockPos();
        int sectionIndex = -1;
        for (final LevelChunkSection section : this.getSections()) {
            sectionIndex++;
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            final int yBase = SectionPos.sectionToBlockCoord(this.getSectionYFromSectionIndex(sectionIndex));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        final FluidState state = section.getFluidState(x, y, z);
                        if (!state.isEmpty()) {
                            this.fluidData.setFluidState(tmpPos.set(x, yBase + y, z), state);
                        }
                    }
                }
            }
        }
    }

    @Override
    public FluidStateManager.ChunkFluidData vs$getFluidData() {
        return this.fluidData;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    public void postSetBlockState(final BlockPos pos, final BlockState state, final boolean moved,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState prevState = cir.getReturnValue();
        if (prevState == null) {
            return;
        }
        // This function is getting invoked by non-game threads for some reason. So use executeOrSchedule() to schedule
        // onSetBlock() to be run on the next tick when this function is invoked by a non-game thread.
        // See https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/913 for more info.
        VSGameUtilsKt.executeOrSchedule(level, () -> BlockStateInfo.INSTANCE.onSetBlock(level, pos, prevState, state));
        this.fluidData.setFluidState(pos, state.getFluidState());
    }

    @Shadow
    public abstract void clearAllBlockEntities();

    @Shadow
    public abstract void registerTickContainerInLevel(ServerLevel serverLevel);

    @Shadow
    public abstract void unregisterTickContainerFromLevel(ServerLevel serverLevel);

    @Override
    public void clearChunk() {
        clearAllBlockEntities();
        unregisterTickContainerFromLevel((ServerLevel) level);

        // Set terrain to empty
        heightmaps.clear();
        Arrays.fill(sections, null);
        final Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] != null) continue;
            //new LevelChunkSection(registry);
            sections[i] = new LevelChunkSection(registry);
        }
        this.fluidData.clear();
        this.setLightCorrect(false);

        registerTickContainerInLevel((ServerLevel) level);
        this.unsaved = true;
    }

    @Override
    public void copyChunkFromOtherDimension(@NotNull final VSLevelChunk srcChunkVS) {
        clearAllBlockEntities();
        unregisterTickContainerFromLevel((ServerLevel) level);

        // Set terrain to empty
        heightmaps.clear();
        Arrays.fill(sections, null);

        // Copy heightmap and sections and block entities from srcChunk
        final LevelChunk srcChunk = (LevelChunk) srcChunkVS;
        final CompoundTag compoundTag = ChunkSerializer.write((ServerLevel) srcChunk.getLevel(), srcChunk);
        // Set status to be ProtoChunk to fix block entities not saving
        compoundTag.putString("Status", ChunkStatus.ChunkType.PROTOCHUNK.name());
        final ProtoChunk protoChunk = ChunkSerializer.read((ServerLevel) level, ((ServerLevel) level).getPoiManager(), chunkPos, compoundTag);

        this.blockTicks = protoChunk.unpackBlockTicks();
        this.fluidTicks = protoChunk.unpackFluidTicks();
        // Copy data from the protoChunk
        // this.chunkPos = chunkPos;
        // this.upgradeData = upgradeData;
        // this.levelHeightAccessor = levelHeightAccessor;

        for (int i = 0; i < sections.length; i++) {
            sections[i] = protoChunk.getSection(i);
        }
        final Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (int i = 0; i < sections.length; ++i) {
            if (sections[i] != null) continue;
            sections[i] = new LevelChunkSection(registry);
        }

        // this.inhabitedTime = l;
        // this.postProcessing = new ShortList[levelHeightAccessor.getSectionsCount()];
        this.blendingData = protoChunk.getBlendingData();

        for (final BlockEntity blockEntity : protoChunk.getBlockEntities().values()) {
            this.setBlockEntity(blockEntity);
        }
        this.pendingBlockEntities.putAll(protoChunk.getBlockEntityNbts());
        for (int i = 0; i < protoChunk.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protoChunk.getPostProcessing()[i];
        }
        this.setAllStarts(protoChunk.getAllStarts());
        this.setAllReferences(protoChunk.getAllReferences());

        // Recompute height maps instead of getting them from protoChunk (This fixes crashes from missing height maps)
        Heightmap.primeHeightmaps(this, ALL_HEIGHT_MAP_TYPES);
        this.setLightCorrect(false);

        registerTickContainerInLevel((ServerLevel) level);

        this.unsaved = true;
    }
}
