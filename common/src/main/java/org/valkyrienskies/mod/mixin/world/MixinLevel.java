package org.valkyrienskies.mod.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectServer;
import org.valkyrienskies.mod.api.ShipBlockEntity;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Level.class)
public class MixinLevel {

    @Shadow
    @Final
    public boolean isClientSide;

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    public void onSetBlockEntity(final BlockPos blockPos, final BlockEntity blockEntity, final CallbackInfo ci) {
        if (!this.isClientSide) {
            final ShipObjectServer obj = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) (Object) this, blockPos);
            if (obj != null && blockEntity instanceof ShipBlockEntity) {
                ((ShipBlockEntity) blockEntity).setShip(obj);
            }
        }
    }

}
