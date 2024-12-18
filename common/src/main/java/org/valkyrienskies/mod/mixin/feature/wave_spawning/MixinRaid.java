package org.valkyrienskies.mod.mixin.feature.wave_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Raid.class)
public abstract class MixinRaid {
    @Shadow
    public abstract BlockPos getCenter();

    @Shadow
    protected abstract void setCenter(BlockPos blockPos);

    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(method = "getValidSpawnPos", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raid;findRandomSpawnPos(II)Lnet/minecraft/core/BlockPos;"))
    private BlockPos onFindRandomSpawnPos(Raid instance, int i, int j, Operation<BlockPos> original) {
        BlockPos originalCenter = this.getCenter();
        this.setCenter(new BlockPos(VSGameUtilsKt.toWorldCoordinates(this.level, originalCenter)));
        BlockPos result = original.call(instance, i, j);
        this.setCenter(originalCenter);
        if (result != null) {
            return result;
        } else {
            return original.call(instance, i, j);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raid;findRandomSpawnPos(II)Lnet/minecraft/core/BlockPos;"))
    private BlockPos onFindRandomSpawnPos2(Raid instance, int i, int j, Operation<BlockPos> original) {
        BlockPos originalCenter = this.getCenter();
        this.setCenter(new BlockPos(VSGameUtilsKt.toWorldCoordinates(this.level, originalCenter)));
        BlockPos result = original.call(instance, i, j);
        this.setCenter(originalCenter);
        if (result != null) {
            return result;
        } else {
            return original.call(instance, i, j);
        }
    }
}
