package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.turntable.TurntableHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(TurntableHandler.class)
public class MixinTurntableHandler {
    @Inject(
        method = "gameRenderTick",
        at = @At("HEAD"),
        remap = false
    )
    private static void wrapRenderTick(CallbackInfo ci, @Share("ship") final LocalRef<Ship> shipOn) {
        final Player player = Minecraft.getInstance().player;
        if (player != null) {
            shipOn.set(VSGameUtilsKt.getShipManagingPos(player.level(), player.getOnPos()));
        }
    }

    @WrapOperation(
        method = "gameRenderTick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;blockPosition()Lnet/minecraft/core/BlockPos;")
    )
    private static BlockPos wrapBlockPos(final LocalPlayer player, final Operation<BlockPos> original,
        @Share("ship") final LocalRef<Ship> shipOn) {
        if(shipOn.get() != null) {
            return player.getOnPos();
        } else return original.call(player);
    }

    @WrapOperation(
        method = "gameRenderTick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;position()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapPosition(final LocalPlayer player, final Operation<Vec3> original,
        @Share("ship") final LocalRef<Ship> shipOn) {
        final Vector3d result = VectorConversionsMCKt.toJOML(original.call(player));
        if (shipOn.get() != null) {
            shipOn.get().getWorldToShip().transformPosition(result);
        }
        return VectorConversionsMCKt.toMinecraft(result);
    }
}
