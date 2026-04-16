package org.valkyrienskies.mod.mixin.feature.submarines;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(WaterFluid.class)
public class MixinWaterFluid {

    @Inject(
        method = "animateTick",
        at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
        cancellable = true
    )
    private void preAnimateTick(Level level, BlockPos blockPos, FluidState fluidState, RandomSource randomSource,
        CallbackInfo ci) {
        if (!ValkyrienSkies.isBlockInShipyard(level, blockPos)) {
            Iterable<Ship> ships = ValkyrienSkies.getShipsIntersecting(level, blockPos.getX(), blockPos.getY(), blockPos.getZ());
            for (Ship ship : ships) {
                Vector3dc posInShip = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOMLD(blockPos));
                BlockPos blockPosInShip = BlockPos.containing(VectorConversionsMCKt.toMinecraft(posInShip));
                if (VSGameUtilsKt.isPositionMaybeSealed(level, blockPosInShip) && ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
