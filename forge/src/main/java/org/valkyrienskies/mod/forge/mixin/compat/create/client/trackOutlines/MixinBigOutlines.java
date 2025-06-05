package org.valkyrienskies.mod.forge.mixin.compat.create.client.trackOutlines;

import com.simibubi.create.foundation.block.BigOutlines;
import com.simibubi.create.foundation.utility.RaycastHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BigOutlines.class)
public class MixinBigOutlines {
    @Unique
    private static boolean valkyrienskies$toShip = false;

    @Unique
    private static Ship valkyrienskies$ship;
    @Unique
    private static Vec3 valkyrienskies$originalOrigin;

    @Inject(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
        ), locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void injectPick(final CallbackInfo ci, final Minecraft mc) {
        if (mc.hitResult != null && mc.level != null && mc.player != null && mc.hitResult.getType() == Type.BLOCK) {
            valkyrienskies$toShip = false;
            final boolean playerOnShip = VSGameUtilsKt.isBlockInShipyard(mc.level, mc.player.getOnPos());
            final boolean hitResultOnShip =
                VSGameUtilsKt.isBlockInShipyard(mc.level, ((BlockHitResult) mc.hitResult).getBlockPos());
            if (playerOnShip && !hitResultOnShip) {
                valkyrienskies$ship = VSGameUtilsKt.getShipManagingPos(mc.level, mc.player.getOnPos());
                //if blockstate is air then transform to ship
                valkyrienskies$toShip = mc.level.getBlockState(BlockPos.containing(mc.hitResult.location)).isAir();
            } else if (hitResultOnShip) {
                valkyrienskies$toShip = true;
                valkyrienskies$ship =
                    VSGameUtilsKt.getShipManagingPos(mc.level, ((BlockHitResult) mc.hitResult).getBlockPos());
            }
        }
    }

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private static Vec3 redirectedOrigin(final LocalPlayer instance, final float v) {
        final Vec3 eyePos = instance.getEyePosition(v);
        if (valkyrienskies$toShip) {
            valkyrienskies$originalOrigin = eyePos;
            return VectorConversionsMCKt.toMinecraft(
                valkyrienskies$ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(eyePos)));
        } else {
            return eyePos;
        }
    }

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ),
        remap = false
    )
    private static Vec3 redirectedTarget(final Player playerIn, final double range, final Vec3 origin) {
        if (valkyrienskies$toShip) {
            return VectorConversionsMCKt.toMinecraft(
                valkyrienskies$ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(
                    RaycastHelper.getTraceTarget(playerIn, range, valkyrienskies$originalOrigin))));
        } else {
            return RaycastHelper.getTraceTarget(playerIn, range, origin);
        }
    }
}
