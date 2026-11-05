package org.valkyrienskies.mod.mixin.feature.poi;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
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
}
