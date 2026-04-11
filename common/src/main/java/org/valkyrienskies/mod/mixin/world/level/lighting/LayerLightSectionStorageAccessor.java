package org.valkyrienskies.mod.mixin.world.level.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SuppressWarnings("rawtypes")
@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageAccessor {

    @Accessor("visibleSectionData")
    DataLayerStorageMap vs$getVisibleSectionData();

    @Accessor("updatingSectionData")
    DataLayerStorageMap vs$getUpdatingSectionData();

    @Accessor("queuedSections")
    Long2ObjectMap vs$getQueuedSections();
}
