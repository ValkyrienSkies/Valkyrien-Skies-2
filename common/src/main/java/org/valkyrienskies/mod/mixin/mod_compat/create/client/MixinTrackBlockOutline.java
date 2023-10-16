package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.foundation.utility.RaycastHelper;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(TrackBlockOutline.class)
public class MixinTrackBlockOutline {
    @Unique
    private static boolean isShip = false;
    @Unique
    private static BlockPos shipBlockPos;

    @Inject(
            method = "pickCurves",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
            ), locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void stuff(final CallbackInfo ci, final Minecraft mc) {
        if (mc.hitResult != null && mc.level != null && mc.hitResult.getType() == Type.BLOCK) {
            shipBlockPos = ((BlockHitResult) mc.hitResult).getBlockPos();

            final List<Vector3d>
                    ships = VSGameUtilsKt.transformToNearbyShipsAndWorld(mc.level, shipBlockPos.getX(), shipBlockPos.getY(),
                    shipBlockPos.getZ(), 10);
            isShip = !ships.isEmpty();
        }
    }

    @Redirect(
            method = "pickCurves()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 redirectedOrigin(final LocalPlayer instance, final float v) {
        final Vec3 eyePos = instance.getEyePosition(v);
        if (isShip) {
            final List<Vector3d>
                    ships = VSGameUtilsKt.transformToNearbyShipsAndWorld(instance.level, eyePos.x, eyePos.y, eyePos.z, 10);
            if (ships.isEmpty()) {
                return eyePos;
            }
            final Vector3d tempVec = ships.get(0);
            return new Vec3(tempVec.x, tempVec.y, tempVec.z);
        } else {
            return eyePos;
        }
    }

    @Redirect(
            method = "pickCurves()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 redirectedTarget(final Player playerIn, final double range, final Vec3 origin) {
        if (isShip) {
            return new Vec3(shipBlockPos.getX(), shipBlockPos.getY(), shipBlockPos.getZ());
        } else {
            return RaycastHelper.getTraceTarget(playerIn, range, origin);
        }
    }
}
