package org.valkyrienskies.mod.forge.mixin.compat.flywheel;

import dev.engine_room.flywheel.backend.SkyLightSectionStorageExtension;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

@Mixin(SkyLightSectionStorage.class)
public abstract class SkyLightSectionStorageMixinMixin extends LayerLightSectionStorage implements SkyLightSectionStorageExtension {
    protected SkyLightSectionStorageMixinMixin() {
        super(null, null, null);
    }

    @Override
    @Nullable
    public DataLayer flywheel$skyDataLayer(long section) {
            return null;

    }

}
