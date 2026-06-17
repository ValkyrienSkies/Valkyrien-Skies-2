package org.valkyrienskies.mod.mixin.feature.ai.iron_golem;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes inherited RandomStrollGoal.mob for subclass mixins (Mixin doesn't follow @Shadow into inherited fields).
@Mixin(RandomStrollGoal.class)
public interface RandomStrollGoalAccessor {
    @Accessor("mob")
    PathfinderMob vs$getMob();
}
