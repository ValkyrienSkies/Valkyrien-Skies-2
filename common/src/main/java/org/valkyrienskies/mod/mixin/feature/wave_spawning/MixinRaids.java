package org.valkyrienskies.mod.mixin.feature.wave_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Raids.class)
public class MixinRaids {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private Map<Integer, Raid> raidMap;

    @WrapOperation(method = "getNearbyRaid", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/raid/Raid;getCenter()Lnet/minecraft/core/BlockPos;"))
    private BlockPos onGetNearbyRaid(Raid instance, Operation<BlockPos> original) {
        return new BlockPos(VSGameUtilsKt.toWorldCoordinates(this.level, original.call(instance)));
    }
}
