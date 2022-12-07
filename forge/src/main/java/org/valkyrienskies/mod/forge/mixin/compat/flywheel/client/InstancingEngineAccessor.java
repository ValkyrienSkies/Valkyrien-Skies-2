package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = InstancingEngine.class, remap = false)
public interface InstancingEngineAccessor {
    @Accessor
    void setOriginCoordinate(BlockPos pos);

}
