package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = ChainConveyorRidingHandler.class)
public abstract class MixinChainConveyorRidingHandler {
    @Shadow
    public static BlockPos ridingChainConveyor;
    @Unique
    private static ClientShip vs$ridingShip;

    @Inject(
        method = "embark",
        at = @At("HEAD")
    )
    private static void preEmbark(BlockPos lift, float position, BlockPos connection, CallbackInfo ci) {
        vs$ridingShip = VSClientGameUtils.getClientShip(lift.getX(), lift.getY(), lift.getZ());
        Player player = Minecraft.getInstance().player;
        if (player != null && vs$ridingShip != null) ((IEntityDraggingInformationProvider)player).getDraggingInformation().setLastShipStoodOn(vs$ridingShip.getId());
    }

    @Inject(
        method = "stopRiding",
        at = @At("HEAD"),
        remap = false
    )
    private static void preStopRiding(CallbackInfo ci)
    {
        vs$ridingShip = null;
    }

    @Inject(
        method = "clientTick",
        at = @At("HEAD"),
        remap = false
    )
    private static void preTick(CallbackInfo ci){
        if (ridingChainConveyor == null) {
            vs$ridingShip = null;
        } else if (vs$ridingShip != null) {
            Player player = Minecraft.getInstance().player;
            if (player != null) ((IEntityDraggingInformationProvider)player).getDraggingInformation().setLastShipStoodOn(vs$ridingShip.getId());
        }
    }

    /*
        Transforms the chain start to the world.
     */
    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity$ConnectionStats;start()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapStart(ConnectionStats instance, Operation<Vec3> original){
        Vec3 origPos = original.call(instance);
        if (vs$ridingShip != null) {
            Vector3d newPos = vs$ridingShip.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }

    /*
        Transforms the chain end to the world.
     */
    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity$ConnectionStats;end()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapEnd(ConnectionStats instance, Operation<Vec3> original){
        Vec3 origPos = original.call(instance);
        if (vs$ridingShip != null) {
            Vector3d newPos = vs$ridingShip.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }
    /*
        Transforms the Pulley position to the world.
     */
    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;", remap = true)
    )
    private static Vec3 wrapBottomCenterOfConveyor(Vec3i vec3i, Operation<Vec3> original){
        Vec3 origPos = original.call(vec3i);
        if (vs$ridingShip != null) {
            Vector3d newPos = vs$ridingShip.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }

    /*
        Sets the player to the right angle when they're hanging right under the conveyor pulley.
     */
    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;rotate(Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/core/Direction$Axis;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapConveyorAngle(Vec3 vec, double deg, Axis axis, Operation<Vec3> original){
        Vec3 result = original.call(vec, deg, axis);
        if (vs$ridingShip != null) {
            Vector3d resultD = VectorConversionsMCKt.toJOML(result);
            vs$ridingShip.getRenderTransform().getShipToWorld().transformDirection(resultD);
            return VectorConversionsMCKt.toMinecraft(resultD);
        }
        return result;
    }

    @WrapOperation(
        method = "updateTargetPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getLookAngle()Lnet/minecraft/world/phys/Vec3;", remap = true)
    )
    private static Vec3 wrapLookVectorToShip(final LocalPlayer player, final Operation<Vec3> original) {
        if(vs$ridingShip != null){
            final Vector3d lookVectorWorld = VectorConversionsMCKt.toJOML(original.call(player));
            vs$ridingShip.getRenderTransform().getWorldToShip().transformDirection(lookVectorWorld);
            return VectorConversionsMCKt.toMinecraft(lookVectorWorld);
        }
        return original.call(player);
    }
}
