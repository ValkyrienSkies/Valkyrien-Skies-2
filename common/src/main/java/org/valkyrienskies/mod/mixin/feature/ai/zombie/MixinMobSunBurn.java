package org.valkyrienskies.mod.mixin.feature.ai.zombie;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

// Mob.isSunBurnTick's canSeeSky check is world-only — sun-sensitive mobs (zombies, skeletons, husks, strays) burn under ship hulls because hulls don't update world skylight. The +1 slack handles the FP edge case where the entity's blockPosition floors to the deck-block Y.
@Mixin(Mob.class)
public abstract class MixinMobSunBurn {

    @WrapOperation(
        method = "isSunBurnTick",
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
