package org.valkyrienskies.mod.mixin.feature.screen_distance_check;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Container.class)
public interface MixinContainer {
    @WrapOperation(method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"))
    private static double vs$containerDistance(Player player, double x, double y, double z, Operation<Double> distance) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!VSGameUtilsKt.isBlockInShipyard(player.level(), pos)) return distance.call(player, x, y, z);
        return VSGameUtilsKt.squaredDistanceToPhysicalBlock(player.level(), pos, player.position());
    }
}
