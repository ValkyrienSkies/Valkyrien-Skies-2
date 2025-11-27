package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.behavior.GoToClosestVillage;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(GoToClosestVillage.class)
public class MixinGoToClosestVillage {
    @WrapOperation(method = "method_46937", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;sectionsToVillage(Lnet/minecraft/core/SectionPos;)I", ordinal = 0))
    private static int onSectionsToVillageInitial(PoiManager poiManager, SectionPos sectionPos, Operation<Integer> original, @Local Villager villager) {
        int[] currentLevels = {original.call(poiManager, sectionPos)};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(villager.level(), villager.getX(), villager.getY(), villager.getZ(), 100, (double x, double y, double z) -> {
            currentLevels[0] = Math.min(currentLevels[0], original.call(poiManager, SectionPos.of(BlockPos.containing(x, y, z))));
        });
        return currentLevels[0];
    }

    @WrapOperation(method = "method_46937", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;sectionsToVillage(Lnet/minecraft/core/SectionPos;)I", ordinal = 1))
    private static int onSectionsToVillageVec3(PoiManager poiManager, SectionPos sectionPos, Operation<Integer> original, @Local Villager villager, @Local(ordinal = 1) Vec3 vec32) {
        int[] currentLevels = {original.call(poiManager, sectionPos)};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(villager.level(), vec32.x, vec32.y, vec32.z, 100, (double x, double y, double z) -> {
            currentLevels[0] = Math.min(currentLevels[0], original.call(poiManager, SectionPos.of(BlockPos.containing(x, y, z))));
        });
        return currentLevels[0];
    }
}
