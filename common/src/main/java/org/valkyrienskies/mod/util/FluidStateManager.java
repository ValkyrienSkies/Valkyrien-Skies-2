package org.valkyrienskies.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.Arrays;

public class FluidStateManager {
	private FluidStateManager() {}

	public static BlockState getBlockState(final Level level, final BlockPos pos) {
		return getBlockState(level, pos, null);
	}

	public static BlockState getBlockState(final Level level, final BlockPos pos, final QueryCache cache) {
		if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
			return Blocks.VOID_AIR.defaultBlockState();
		}
		final ChunkAccess chunk = getCachedChunk(
			level,
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ()),
			cache
		);
		if (chunk == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return chunk.getBlockState(pos);
	}

	public static FluidState getFluidState(final Level level, final BlockPos pos) {
		return getFluidState(level, pos, null);
	}

	public static FluidState getFluidState(final Level level, final BlockPos pos, final QueryCache cache) {
		if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
			return Fluids.EMPTY.defaultFluidState();
		}
		final ChunkAccess chunk = getCachedChunk(
			level,
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ()),
			cache
		);
		if (chunk == null) {
			return Fluids.EMPTY.defaultFluidState();
		}
		return chunk.getFluidState(pos);
	}

	public static ChunkAccess getCachedChunk(final Level level, final int chunkX, final int chunkZ, final QueryCache cache) {
		if (cache != null) {
			return cache.getChunk(level, chunkX, chunkZ);
		}
		return level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
	}

	public static final class QueryCache {
		private static final int CACHE_SIZE = 64;
		private static final int CACHE_MASK = CACHE_SIZE - 1;

		private final Level[] levels = new Level[CACHE_SIZE];
		private final long[] chunkKeys = new long[CACHE_SIZE];
		private final ChunkAccess[] chunks = new ChunkAccess[CACHE_SIZE];
		private final boolean[] occupied = new boolean[CACHE_SIZE];

		public void reset() {
			Arrays.fill(this.levels, null);
			Arrays.fill(this.chunks, null);
			Arrays.fill(this.occupied, false);
		}

		private ChunkAccess getChunk(final Level level, final int chunkX, final int chunkZ) {
			final long key = ChunkPos.asLong(chunkX, chunkZ);
			final int slot = slotFor(key);
			if (this.occupied[slot] && this.levels[slot] == level && this.chunkKeys[slot] == key) {
				return this.chunks[slot];
			}

			final ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
			this.occupied[slot] = true;
			this.levels[slot] = level;
			this.chunkKeys[slot] = key;
			this.chunks[slot] = chunk;
			return chunk;
		}

		private static int slotFor(final long key) {
			long h = key;
			h ^= h >>> 33;
			h *= 0xff51afd7ed558ccdL;
			h ^= h >>> 33;
			h *= 0xc4ceb9fe1a85ec53L;
			h ^= h >>> 33;
			return ((int) h) & CACHE_MASK;
		}
	}
}
