package org.valkyrienskies.mod.forge.mixin.feature.forge_interact;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.ForgeMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinUsePacket {
    @WrapOperation(method = "handleUseItemOn", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;canReachRaw(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean vs$rawUseReach(ServerPlayer player, BlockPos pos, double padding, Operation<Boolean> original,
        @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        if (!VSGameUtilsKt.isBlockInShipyard(player.level(), pos) || !VSGameConfig.SERVER.getEnableInteractDistanceChecks())
            return original.call(player, pos, padding);
        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) + padding;
        return VSGameUtilsKt.squaredDistanceToUseHit(player.level(), packet.getHitResult(), player.getEyePosition()) <= reach * reach;
    }

    @WrapOperation(method = "handleUseItemOn", require = 0, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerPlayer;canReach(Lnet/minecraft/core/BlockPos;D)Z", remap = false))
    private boolean vs$useReach(ServerPlayer player, BlockPos pos, double padding, Operation<Boolean> original,
        @Local(argsOnly = true) ServerboundUseItemOnPacket packet) {
        if (!VSGameUtilsKt.isBlockInShipyard(player.level(), pos) || !VSGameConfig.SERVER.getEnableInteractDistanceChecks())
            return original.call(player, pos, padding);
        double reach = player.getBlockReach() + padding;
        return VSGameUtilsKt.squaredDistanceToUseHit(player.level(), packet.getHitResult(), player.getEyePosition()) < reach * reach;
    }
}
