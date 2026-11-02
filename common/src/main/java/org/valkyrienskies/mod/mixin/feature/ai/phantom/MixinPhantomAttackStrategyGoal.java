package org.valkyrienskies.mod.mixin.feature.ai.phantom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

// Phantom$PhantomAttackStrategyGoal.stop re-anchors the phantom Y to the world heightmap; for an airship the world heightmap is the sea floor far below the ship and the phantom drops out of the sky to orbit at ground level. Use the ship-include heightmap so it anchors above the deck.
@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomAttackStrategyGoal")
public abstract class MixinPhantomAttackStrategyGoal {

    @WrapOperation(
        method = "stop",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$heightmapIncludingShips(
        final Level level, final Heightmap.Types types, final BlockPos pos,
        final Operation<BlockPos> original
    ) {
        return CompatUtil.INSTANCE.getWorldHeightmapPosIncludingShips(level, types, pos);
    }
}
