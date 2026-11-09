package org.valkyrienskies.mod.mixin.accessors.world.level.chunk;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkSection.class)
public interface LevelChunkSectionAccessor {
    /**
     * Number of non-air blocks in the section. Used by the section-compile
     * fast path to short-circuit the 4096-block iteration once every non-air
     * block has been visited — for a 1-block ship section that's ~1 iteration
     * of useful work instead of 4096.
     */
    @Accessor("nonEmptyBlockCount")
    short vs$getNonEmptyBlockCount();
}
