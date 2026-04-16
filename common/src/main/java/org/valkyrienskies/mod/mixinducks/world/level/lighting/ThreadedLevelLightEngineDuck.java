package org.valkyrienskies.mod.mixinducks.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Duck interface for ThreadedLevelLightEngine that allows direct (synchronous)
 * calls to the underlying LevelLightEngine, bypassing the async task queue.
 *
 * Used by ShipAssembler.initSkyLightForShip to set up lighting state immediately
 * after assembly, so that chunk packets contain correct light data.
 */
public interface ThreadedLevelLightEngineDuck {

    void vsDirectUpdateSectionStatus(SectionPos sectionPos, boolean hasOnlyAir);

    void vsDirectSetLightEnabled(ChunkPos chunkPos, boolean enabled);

    void vsDirectPropagateLightSources(ChunkPos chunkPos);

    void vsDirectCheckBlock(BlockPos pos);

    int vsDirectRunLightUpdates();
}
