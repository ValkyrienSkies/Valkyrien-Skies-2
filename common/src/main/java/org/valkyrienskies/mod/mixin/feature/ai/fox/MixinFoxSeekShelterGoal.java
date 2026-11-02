package org.valkyrienskies.mod.mixin.feature.ai.fox;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

// Fox$SeekShelterGoal overrides FleeSunGoal.canUse, so MixinFleeSunGoal's wrap doesn't
// reach the override's own canSeeSky calls — duplicate the wrap here.
@Mixin(targets = "net.minecraft.world.entity.animal.Fox$SeekShelterGoal")
public abstract class MixinFoxSeekShelterGoal {

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$canSeeSkyIncludingShips(
        final Level instance, final BlockPos pos, final Operation<Boolean> original
    ) {
        if (!original.call(instance, pos)) return false;
        final BlockPos heightInclShips =
            CompatUtil.INSTANCE.getWorldHeightmapPosIncludingShips(instance, Heightmap.Types.MOTION_BLOCKING, pos);
        return pos.getY() + 1 >= heightInclShips.getY();
    }
}
