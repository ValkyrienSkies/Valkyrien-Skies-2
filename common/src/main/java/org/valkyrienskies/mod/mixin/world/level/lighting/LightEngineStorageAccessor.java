package org.valkyrienskies.mod.mixin.world.level.lighting;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SuppressWarnings("rawtypes")
@Mixin(LightEngine.class)
public interface LightEngineStorageAccessor {

    @Accessor("storage")
    LayerLightSectionStorage vs$getStorage();
}
