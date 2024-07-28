package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;
import org.valkyrienskies.mod.compat.flywheel.ShipEffect;

@Mixin(ClientChunkCache.class)
public class MixinClientChunkCache {
    @Shadow
    @Final
    ClientLevel level;

    @Inject(method = "onLightUpdate", at = @At("HEAD"))
    private void vs_flywheel$onLightUpdate(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        if (!FlywheelCompat.INSTANCE.isFlywheelInstalled()) return;

        ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, pos.getX(), pos.getZ());
        if (ship != null) {
            var manager = ((VisualManagerImpl) ShipEffect.Companion.getShipEffect(ship).getManager$valkyrienskies_120());
            if (manager != null) manager.onLightUpdate(pos.asLong());
        }

    }

}
