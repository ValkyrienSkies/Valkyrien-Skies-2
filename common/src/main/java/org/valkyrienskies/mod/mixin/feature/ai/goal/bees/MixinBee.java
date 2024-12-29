package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bee.class)
public abstract class MixinBee extends Entity {

    public MixinBee(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapOperation(method = "closerThan", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private boolean onCloserThan(BlockPos instance, Vec3i vec3i, double v, Operation<Boolean> original) {
        return original.call(new BlockPos(VSGameUtilsKt.toWorldCoordinates(this.level, instance)), vec3i, v);
    }
}
