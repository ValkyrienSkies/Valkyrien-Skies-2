package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;
import org.valkyrienskies.mod.compat.flywheel.ShipEffect;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity {
    @Shadow
    @Nullable
    protected Level level;

    @Shadow
    public abstract BlockPos getBlockPos();

    @Inject(method = "setRemoved()V", at = @At("TAIL"))
    private void vs_flywheel$removeVisual(CallbackInfo ci) {
        if (!level.isClientSide) return;
        if (LoadedMods.getFlywheel() != FlywheelVersion.V1) return;
        if (!FlywheelCompat.INSTANCE.validate((BlockEntity) (Object) this, level)) return;

        ClientShip ship = (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(level, this.getBlockPos());
        if (ship == null) return;

        ShipEffect.Companion.getShipEffect(ship).queueRemoval((BlockEntity) (Object) this);
    }
}
