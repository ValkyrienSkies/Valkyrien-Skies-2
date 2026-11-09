package org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Path.class)
public interface PathAccessor {

    @Accessor("target")
    BlockPos vs$getRawTarget();
}
