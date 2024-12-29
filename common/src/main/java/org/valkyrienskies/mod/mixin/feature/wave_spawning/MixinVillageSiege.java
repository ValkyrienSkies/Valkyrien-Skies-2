package org.valkyrienskies.mod.mixin.feature.wave_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(VillageSiege.class)
public class MixinVillageSiege {
    @WrapOperation(method = "tryToSetupSiege", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/VillageSiege;findRandomSpawnPos(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 onFindRandomSpawnPos(VillageSiege instance, ServerLevel k, BlockPos l, Operation<Vec3> original) {
        BlockPos transformedCenter = new BlockPos(VSGameUtilsKt.toWorldCoordinates(k, l));
        Vec3 result = original.call(instance, k, transformedCenter);
        if (result != null) {
            return result;
        } else {
            return original.call(instance, k, l);
        }
    }

    @WrapOperation(method = "trySpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/VillageSiege;findRandomSpawnPos(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 onFindRandomSpawnPos2(VillageSiege instance, ServerLevel k, BlockPos l, Operation<Vec3> original) {
        BlockPos transformedCenter = new BlockPos(VSGameUtilsKt.toWorldCoordinates(k, l));
        Vec3 result = original.call(instance, k, transformedCenter);
        if (result != null) {
            return result;
        } else {
            return original.call(instance, k, l);
        }
    }
}
