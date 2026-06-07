package org.valkyrienskies.mod.fabric.mixin.server.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow public ServerPlayer player;

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        ),
        method = "handleUseItemOn"
    )
    private double vs$useDistance(Vec3 eye, Vec3 center, Operation<Double> original,
        @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        if (!VSGameUtilsKt.isBlockInShipyard(player.level(), packet.getHitResult().getBlockPos())
            || !VSGameConfig.SERVER.getEnableInteractDistanceChecks()) return 0;
        return VSGameUtilsKt.squaredDistanceToUseHit(player.level(), packet.getHitResult(), eye);
    }

    @WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(DDD)D"))
    private double vs$distanceBeforeUse(ServerPlayer player, double x, double y, double z, Operation<Double> original,
        @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        if (!VSGameUtilsKt.isBlockInShipyard(player.level(), packet.getHitResult().getBlockPos())) {
            return original.call(player, x, y, z);
        }
        return VSGameUtilsKt.squaredDistanceToUseHit(player.level(), packet.getHitResult(), player.position());
    }

    @Redirect(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;abs(D)D"
        )
    )
    private double stopFarAwayReturn(double a) {
        return 0.0;
    }

    @WrapOperation(
        method = "handleInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"
        )
    )
    AABB getWorldAABB(Entity entity, Operation<AABB> getAABB) {
        return VSGameUtilsKt.transformAabbToWorld(entity.level(), getAABB.call(entity));
    }
}
