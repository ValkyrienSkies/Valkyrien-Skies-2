package org.valkyrienskies.mod.mixin.feature.ship_pos_cache;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(ChunkPos.class)
public class MixinChunkPos implements OfShip {
    @Unique
    protected Ship valkyrienskies$shipCache;

    @Override
    public Ship getShip() {
        return valkyrienskies$shipCache;
    }

    @Override
    public void setShip(Ship ship) {
        valkyrienskies$shipCache = ship;
    }

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void carryShipCacheFromBlockPos(BlockPos blockPos, CallbackInfo ci) {
        this.valkyrienskies$shipCache = ((OfShip) blockPos).getShip();
    }

    @ModifyReturnValue(method = {"getBlockAt", "getMiddleBlockPosition", "getWorldPosition"}, at = @At("RETURN"))
    private BlockPos carryShipCacheToBlockPos(BlockPos original) {
        ((OfShip)original).setShip(valkyrienskies$shipCache);
        return original;
    }

    @ModifyReturnValue(method = "rangeClosed(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/stream/Stream;", at = @At("RETURN"))
    private static Stream<ChunkPos> carryShipCacheToSectionPosStream(Stream<ChunkPos> original, ChunkPos pos) {
        if (pos instanceof OfShip ship) {
            return original.peek((s) -> {
                if (s instanceof OfShip sh)
                    sh.setShip(ship);
            });
        }
        return original;
    }
}
