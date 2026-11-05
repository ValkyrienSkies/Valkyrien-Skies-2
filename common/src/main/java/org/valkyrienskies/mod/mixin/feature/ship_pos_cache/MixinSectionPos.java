package org.valkyrienskies.mod.mixin.feature.ship_pos_cache;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(SectionPos.class)
public class MixinSectionPos implements OfShip {
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

    @ModifyReturnValue(method = "of(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/SectionPos;", at = @At("RETURN"))
    private static SectionPos carryShipCacheFromBlockPos(SectionPos original, BlockPos blockPos) {
        if (blockPos instanceof OfShip ship)
            ((OfShip)original).setShip(ship);
        return original;
    }

    @ModifyReturnValue(method = "of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;", at = @At("RETURN"))
    private static SectionPos carryShipCacheFromBlockPos(SectionPos original, ChunkPos chunkPos) {
        if (chunkPos instanceof OfShip ship)
            ((OfShip)original).setShip(ship);
        return original;
    }

    @ModifyReturnValue(method = "bottomOf", at = @At("RETURN"))
    private static SectionPos carryShipCacheFromBlockPos(SectionPos original, ChunkAccess chunkAccess) {
        if (chunkAccess.getPos() instanceof OfShip ship)
            ((OfShip)original).setShip(ship);
        return original;
    }

    @ModifyReturnValue(method = "desc=/\\)Lnet\\/minecraft\\/core\\/BlockPos;/", at = @At("RETURN"))
    private BlockPos carryShipCacheToBlockPos(BlockPos original) {
        if (original instanceof OfShip ship)
            ship.setShip(valkyrienskies$shipCache);
        return original;
    }

    @ModifyReturnValue(method = "desc=/\\)Lnet\\/minecraft\\/world\\/level\\/ChunkPos;/", at = @At("RETURN"))
    private ChunkPos carryShipCacheToBlockPos(ChunkPos original) {
        if (original instanceof OfShip ship)
            ship.setShip(valkyrienskies$shipCache);
        return original;
    }

    @ModifyReturnValue(method = "blocksInside", at = @At("RETURN"))
    private Stream<BlockPos> carryShipCacheToBlockPos(Stream<BlockPos> original) {
        return original.peek(b -> {
            if (b instanceof OfShip ship)
                ship.setShip(valkyrienskies$shipCache);
        } );
    }

    @ModifyReturnValue(method = "cube", at = @At("RETURN"))
    private static Stream<SectionPos> carryShipCacheToSectionPosStream(Stream<SectionPos> original, SectionPos pos) {
        if (pos instanceof OfShip ship) {
            return original.peek((s) -> {
                if (s instanceof OfShip sh)
                    sh.setShip(ship);
            });
        }
        return original;
    }

    @ModifyReturnValue(method = "aroundChunk", at = @At("RETURN"))
    private static Stream<SectionPos> carryShipCacheToSectionPosStream(Stream<SectionPos> original, ChunkPos pos) {
        if (pos instanceof OfShip ship) {
            return original.peek((s) -> {
                if (s instanceof OfShip sh)
                    sh.setShip(ship);
            });
        }
        return original;
    }
}
