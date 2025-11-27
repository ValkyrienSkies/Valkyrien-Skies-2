package org.valkyrienskies.mod.mixin.feature.sculk;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(EuclideanGameEventListenerRegistry.class)
public abstract class MixinEuclideanGameEventListenerRegistry {
    @Inject(method = "getPostableListenerPosition", at = @At("HEAD"), cancellable = true)
    private static void vsAwareDistance(ServerLevel serverLevel, Vec3 vec3, GameEventListener gameEventListener, CallbackInfoReturnable<Optional<Vec3>> cir) {
        Optional<Vec3> optional = gameEventListener.getListenerSource().getPosition(serverLevel);
        if (optional.isEmpty()) {
            cir.setReturnValue(Optional.empty());
        } else {
            Vec3 orig = optional.get();
            Vec3 sourcePos = vec3;
            Vec3 listenPos = optional.get();
            Ship sourceShip = VSGameUtilsKt.getShipManagingPos(serverLevel, sourcePos);
            Ship listenShip = VSGameUtilsKt.getShipManagingPos(serverLevel, listenPos);
            if (sourceShip != null) sourcePos = VSGameUtilsKt.toWorldCoordinates(sourceShip, sourcePos);
            if (listenShip != null) listenPos = VSGameUtilsKt.toWorldCoordinates(listenShip, listenPos);
            double d = listenPos.distanceToSqr(sourcePos);
            int i = gameEventListener.getListenerRadius() * gameEventListener.getListenerRadius();
            cir.setReturnValue(d > (double) i ? Optional.empty() : optional);
        }
        cir.cancel();
    }
}
