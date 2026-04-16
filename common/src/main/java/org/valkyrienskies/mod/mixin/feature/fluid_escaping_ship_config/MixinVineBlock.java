package org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(VineBlock.class)
public class MixinVineBlock {
    @WrapOperation(
        method = "randomTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
        // Arclight and Ketting both use an @Overwrite on the entire VineBlock behaviour, leading to the @Inject failing.
        // We make this mixin optional to reluctantly support the @Overwrite and prevent a hard crash.
        require = 0
    )
    boolean cancelSpread(ServerLevel level, BlockPos toPos, BlockState blockState, int i, Operation<Boolean> original) {
        if (VSGameConfig.SERVER.getPreventVinesEscapingShip() && level != null) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos((Level) level, toPos);
            if (ship != null && ship.getShipAABB() != null) {
                final AABBic a = ship.getShipAABB();
                final int x = toPos.getX();
                final int y = toPos.getY();
                final int z = toPos.getZ();

                if (x < a.minX() || y < a.minY() || z < a.minZ() || x >= a.maxX() || y >= a.maxY() || z >= a.maxZ()) {
                    return true;
                }
            }
        }
        return original.call(level, toPos, blockState, i);
    }
}
