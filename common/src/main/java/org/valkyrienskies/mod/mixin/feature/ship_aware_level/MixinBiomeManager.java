package org.valkyrienskies.mod.mixin.feature.ship_aware_level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeManager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Biome reads at a shipyard position return the world biome under the ship (mob variants at finalizeSpawn, weather,
// freezing). Server only: client tint caches don't follow a moving ship.
@Mixin(BiomeManager.class)
public abstract class MixinBiomeManager {

    @Shadow
    @Final
    private BiomeManager.NoiseBiomeSource noiseBiomeSource;

    @ModifyVariable(
        method = "getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockPos vs$shipyardToWorld(final BlockPos pos) {
        if (!(noiseBiomeSource instanceof final ServerLevel level)) return pos;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) return pos;
        final Vector3d world = ship.getTransform().getShipToWorld()
            .transformPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new Vector3d());
        return BlockPos.containing(world.x, world.y, world.z);
    }
}
