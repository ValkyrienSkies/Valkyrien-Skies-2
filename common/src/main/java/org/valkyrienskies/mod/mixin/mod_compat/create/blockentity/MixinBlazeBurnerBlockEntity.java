package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlazeBurnerBlockEntity.class)
public abstract class MixinBlazeBurnerBlockEntity extends SmartBlockEntity {

    @ModifyVariable(method = "tickAnimation", at = @At("STORE"), ordinal = 2, remap = false)
    private double worldX(
        final double x, @Local final LocalPlayer player
    ) {
        if (isVirtual()) {
            return x;
        }
        final Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(
            player.level(), getBlockPos().getCenter()
        );
        return player.getX() - worldPos.x;
    }

    @ModifyVariable(method = "tickAnimation", at = @At("STORE"), ordinal = 2, remap = false)
    private double worldZ(
        final double z, @Local final LocalPlayer player
    ) {
        if (isVirtual()) {
            return z;
        }
        final Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(
            player.level(), getBlockPos().getCenter()
        );
        return player.getZ() - worldPos.z;
    }

    @WrapOperation(method = "tickAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;atan2(DD)D"))
    private double adjustYaw(final double d, final double e, final Operation<Double> original,
        @Local final LocalPlayer player) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, getBlockPos());
        if (ship != null) {
            // Get ship's transformation matrix (local-to-world)
            final Matrix4dc shipTransform = ship.getTransform().getShipToWorld();
            final Matrix4d invTransform = new Matrix4d(shipTransform).invertAffine();

            // Transform player's world position to local
            final Vector3d P_w = VectorConversionsMCKt.toJOML(player.position());
            final Vector3d P_l = new Vector3d();
            invTransform.transformPosition(P_w, P_l);

            // Table's position in local space (BlockPos is assumed local for ships)
            final Vector3d T_l = VectorConversionsMCKt.toJOML(getBlockPos().getCenter());

            // Direction in local space
            final Vector3d D_l = new Vector3d(P_l).sub(T_l);

            // Compute local yaw
            return original.call(D_l.z(), D_l.x());
        } else {
            // No ship: Use world coordinates (vanilla behavior)
            return original.call(d, e);
        }
    }

    // Dummy
    private MixinBlazeBurnerBlockEntity(final BlockEntityType<?> type, final BlockPos pos,
        final BlockState state) {
        super(type, pos, state);
    }
}
