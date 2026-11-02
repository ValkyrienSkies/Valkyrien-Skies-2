package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.mob_spawning.ShipFinalizeBiome;

@Mixin(BiomeManager.class)
public abstract class MixinBiomeManagerShipFinalize {

    @ModifyVariable(
        method = "getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockPos vs$projectBiomeToShipWorld(final BlockPos pos) {
        return ShipFinalizeBiome.project(pos);
    }
}
