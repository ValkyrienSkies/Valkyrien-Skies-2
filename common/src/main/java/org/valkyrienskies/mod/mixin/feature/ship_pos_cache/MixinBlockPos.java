package org.valkyrienskies.mod.mixin.feature.ship_pos_cache;

import com.google.common.collect.AbstractIterator;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(BlockPos.class)
public class MixinBlockPos extends MixinVec3i {

    @Inject(method = "<init>(Lnet/minecraft/core/Vec3i;)V", at = @At("TAIL"))
    private void carryShipCacheFromVec3i(Vec3i vec3i, CallbackInfo ci) {
        if (vec3i instanceof OfShip ship)
            valkyrienskies$shipCache = ship.getShip();
    }

    @ModifyReturnValue(method = {
        "relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;",
        "relative(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/BlockPos;",
        "relative(Lnet/minecraft/core/Direction$Axis;I)Lnet/minecraft/core/BlockPos;",
        "rotate",
        "atY"
    }, at = @At(
        "RETURN"))
    private BlockPos carryShipCacheToBlockPos(BlockPos original) {
        if (original instanceof OfShip ship)
            ship.setShip(valkyrienskies$shipCache);
        return original;
    }

    //taking 2 blockPos from 2 different ship shouldn't happen and would be a HUGE performance loss even without this so we assuming it doesn't happen
    @ModifyReturnValue(method = "betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;", at = @At("RETURN"))
    private static Iterable<BlockPos> carryShipCacheToBlockPosStream(Iterable<BlockPos> original, BlockPos pos) {
        if (pos instanceof OfShip ship) {
            return () -> new AbstractIterator<>() {
                final AbstractIterator<BlockPos> originalIterator = ((AbstractIterator<BlockPos>)original.iterator());

                @Override
                protected @Nullable BlockPos computeNext() {
                    BlockPos pos = originalIterator.next();
                    if (!originalIterator.hasNext())
                        this.endOfData();
                    if (pos instanceof OfShip s)
                        s.setShip(ship);
                    return pos;
                }
            };
        }
        return original;
    }
}
