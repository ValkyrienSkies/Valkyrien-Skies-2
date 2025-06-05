package org.valkyrienskies.mod.fabric.mixin.compat.flywheel;

import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(value = InstancingEngine.class, remap = false)
public interface InstancingEngineAccessor {
    @Accessor
    void setOriginCoordinate(BlockPos pos);

}
