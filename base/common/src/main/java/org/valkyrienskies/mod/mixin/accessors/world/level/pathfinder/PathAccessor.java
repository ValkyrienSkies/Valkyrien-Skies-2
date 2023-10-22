package org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder;

import java.util.Set;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Path.class)
public interface PathAccessor {
    
    @Accessor("targetNodes")
    Set<Target> getTargetNodes();

}
