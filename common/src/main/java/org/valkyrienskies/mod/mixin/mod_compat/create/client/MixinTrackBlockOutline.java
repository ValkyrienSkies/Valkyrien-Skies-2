package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.TrackBlockOutline.BezierPointSelection;
import com.simibubi.create.foundation.utility.RaycastHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.impl.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(TrackBlockOutline.class)
public class MixinTrackBlockOutline {

    @Shadow
    public static BezierPointSelection result;
    @Unique
    private static boolean valkyrienskies$toShip = false;

    @Unique
    private static Ship valkyrienskies$ship;
    @Unique
    private static Vec3 valkyrienskies$originalOrigin;

    @Inject(
        method = "pickCurves",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"
        ), locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void stuff(final CallbackInfo ci, final Minecraft mc) {
        if (mc.hitResult != null && mc.level != null && mc.player != null) {
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
        method = "pickCurves",
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
        method = "pickCurves",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
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

    @Unique
    private static Vec3 valkyrienskies$cameraVec3;
    @Unique
    private static Vec3 valkyrienskies$vec;
    @Unique
    private static Vec3 valkyrienskies$angles;

    @Inject(method = "drawCurveSelection",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/TrackBlockOutline$BezierPointSelection;angles()Lnet/minecraft/world/phys/Vec3;"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private static void harvestDrawCurveSelection(final PoseStack ms, final MultiBufferSource buffer, final Vec3 camera,
        final CallbackInfo ci, final Minecraft mc,
        final BezierPointSelection result, final VertexConsumer vb, final Vec3 vec) {
        valkyrienskies$cameraVec3 = camera;
        valkyrienskies$vec = result.vec();
        valkyrienskies$angles = result.angles();
    }

    @Unique
    private static PoseStack vs$modPoseStack(final PoseStack ms) {

        final Quaterniond rotation = new Quaterniond().identity();
        final Quaterniond yawQuat = new Quaterniond().rotateY(valkyrienskies$angles.y);
        final Quaterniond pitchQuat = new Quaterniond().rotateX(valkyrienskies$angles.x);

        final ShipObjectClient ship =
            (ShipObjectClient) VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, valkyrienskies$vec);

        if (ship != null) {
            yawQuat.mul(pitchQuat, rotation);
            ship.getRenderTransform().getShipToWorldRotation().mul(rotation, rotation);

            final Vector3d worldVec = ship.getRenderTransform().getShipToWorld()
                .transformPosition(
                    new Vector3d(valkyrienskies$vec.x, valkyrienskies$vec.y + .125, valkyrienskies$vec.z),
                    new Vector3d());

            ms.popPose();
            ms.pushPose();
            ms.translate(worldVec.x - valkyrienskies$cameraVec3.x,
                worldVec.y - valkyrienskies$cameraVec3.y,
                worldVec.z - valkyrienskies$cameraVec3.z);
            ms.mulPose(VectorConversionsMCKt.toFloat(rotation));
            ms.translate(-.5, -.125f, -.5);
        }
        return ms;
    }

    @ModifyArg(method = "drawCurveSelection",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/track/TrackBlockOutline;renderShape(Lnet/minecraft/world/phys/shapes/VoxelShape;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Ljava/lang/Boolean;)V"),
        index = 1)
    private static PoseStack redirectTransformStackTranslate(final PoseStack ms) {

        final Level level = Minecraft.getInstance().level;
        if (level != null && valkyrienskies$vec != null) {
            final ShipObjectClient ship;
            if ((ship =
                (ShipObjectClient) VSGameUtilsKt.getShipManagingPos(level, valkyrienskies$vec)) !=
                null) {
                return vs$modPoseStack(ms);
            }
        }
        return ms;
    }

    @Unique
    private static Camera valkyrienskies$info;
    @Unique
    private static BlockHitResult valkyrienskies$hitResult;

    @ModifyArg(method = "drawCustomBlockSelection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"))
    private static BlockPos modIsWithinBounds(final BlockPos blockPos) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final Ship ship;
            if ((ship = VSGameUtilsKt.getShipManagingPos(level, blockPos)) != null) {
                return BlockPos.containing(VectorConversionsMCKt.toMinecraft(ship.getShipToWorld()
                    .transformPosition(VectorConversionsMCKt.toJOMLD(blockPos))));
            }
        }
        return blockPos;
    }

    @Inject(method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void harvest(final LevelRenderer context, final Camera info, final HitResult hitResult,
        final float partialTicks,
        final PoseStack ms, final MultiBufferSource buffers, final CallbackInfoReturnable<Boolean> cir) {
        valkyrienskies$info = info;
        valkyrienskies$hitResult = (BlockHitResult) hitResult;
    }

    @Redirect(method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void redirectTranslate(final PoseStack instance, final double d, final double e, final double f) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final ShipObjectClient ship;
            if ((ship = (ShipObjectClient) VSGameUtilsKt.getShipManagingPos(level,
                valkyrienskies$hitResult.getBlockPos())) != null) {
                final Vec3 camPos = valkyrienskies$info.getPosition();
                VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance,
                    valkyrienskies$hitResult.getBlockPos(),
                    camPos.x, camPos.y, camPos.z);
            } else {
                instance.translate(d, e, f);
            }
        } else {
            instance.translate(d, e, f);
        }

    }
}
