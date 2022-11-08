package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigationRegion.class)
public interface PathNavigationRegionAccessor {
    //PathNavigationRegions don't contain ship information, so we need to get that from their included level
    @Accessor("level")
    Level getLevel();

}
