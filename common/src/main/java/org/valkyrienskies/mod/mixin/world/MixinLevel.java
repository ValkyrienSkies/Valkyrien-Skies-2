package org.valkyrienskies.mod.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.api.ShipBlockEntity;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Level.class)
public class MixinLevel {

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    public void onSetBlockEntity(final BlockPos blockPos, final BlockEntity blockEntity, final CallbackInfo ci) {
        final Ship data = VSGameUtilsKt.getShipManagingPos((Level) (Object) this, blockPos);
        if (data != null && blockEntity instanceof ShipBlockEntity) {
            ((ShipBlockEntity) blockEntity).setShip(data);
        }
    }

}
