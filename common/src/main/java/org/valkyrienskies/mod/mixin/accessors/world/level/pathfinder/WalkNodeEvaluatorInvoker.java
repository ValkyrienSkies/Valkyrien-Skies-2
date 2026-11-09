package org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WalkNodeEvaluator.class)
public interface WalkNodeEvaluatorInvoker {

    @Invoker("getBlockPathTypeRaw")
    static BlockPathTypes vs$getBlockPathTypeRaw(final BlockGetter blockGetter, final BlockPos blockPos) {
        throw new AssertionError("mixin not applied");
    }
}
