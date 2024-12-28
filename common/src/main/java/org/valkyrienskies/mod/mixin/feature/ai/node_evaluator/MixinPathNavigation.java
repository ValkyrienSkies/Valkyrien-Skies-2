package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(PathNavigation.class)
public class MixinPathNavigation {
    @Shadow
    @Final
    protected Level level;

    @WrapOperation(method = "moveTo(DDDD)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(DDDI)Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path onMoveToCreatePath(PathNavigation instance, double d, double e, double f, int i,
        Operation<Path> original) {
        Vec3 transformedPos = VSGameUtilsKt.toWorldCoordinates(this.level, new Vec3(d, e, f));
        return original.call(instance, transformedPos.x, transformedPos.y, transformedPos.z, i);
    }
}
