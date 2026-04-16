package org.valkyrienskies.mod.forge.mixin.compat.mekanism;

import mekanism.api.providers.IBlockProvider;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(TileEntityMultiblock.class)
public class MixinTileEntityMultiblock extends TileEntityMekanism {
    @Redirect(method = "doMultiblockSparkle", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"))
    double shipAwareDist(BlockPos pos1, Vec3i pos2) {
        return ValkyrienSkies.distanceSquared(level, (Vec3i)pos1, pos2);
    }

    // Dummy constructor
    public MixinTileEntityMultiblock(IBlockProvider blockProvider, BlockPos pos,
        BlockState state) {
        super(blockProvider, pos, state);
    }
}
