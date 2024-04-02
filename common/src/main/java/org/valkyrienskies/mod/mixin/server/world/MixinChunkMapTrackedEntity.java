package org.valkyrienskies.mod.mixin.server.world;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChunkMap.TrackedEntity.class)
public class MixinChunkMapTrackedEntity {

    @Shadow
    @Final
    Entity entity;

    @Unique
    private Ship inCallShip = null;

    // Changes entity position for tracking into world space if needed
    @ModifyExpressionValue(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerEntity;sentPos()Lnet/minecraft/world/phys/Vec3;"))
    Vec3 includeShips(final Vec3 original) {
        final Vector3d pos = VectorConversionsMCKt.toJOML(original);
        final Ship ship = inCallShip = VSGameUtilsKt.getShipObjectManagingPos(this.entity.level, pos);
        if (ship != null) {
            return VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(pos));
        } else {
            return original;
        }
    }

    @WrapOperation(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"))
    boolean skipWierdCheck(final Entity instance, final ServerPlayer serverPlayer,
        final Operation<Boolean> broadcastToPlayer) {
        return inCallShip != null || broadcastToPlayer.call(instance, serverPlayer);
    }

}
