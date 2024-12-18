package org.valkyrienskies.mod.mixin.feature.ai.goal;

import java.util.Random;
import net.minecraft.world.entity.ai.behavior.AcquirePoi.JitteredLinearRetry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(JitteredLinearRetry.class)
public interface JitteredLinearRetryAccessor {
    @Invoker("<init>")
    static JitteredLinearRetry create(Random random, long l) {
        throw new AssertionError();
    }
}
