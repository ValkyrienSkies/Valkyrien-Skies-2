package org.valkyrienskies.mod.mixin.client;

import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBi;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Camera.class)
public abstract class MixinCamera implements IVSCamera {
    // region Shadow
    @Shadow
    private boolean initialized;
    @Shadow
    private BlockGetter level;
    @Shadow
    private Entity entity;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternion rotation;
    @Shadow
    private boolean detached;
    @Shadow
    private boolean mirror;
    @Unique
    private boolean cameraSeated = false;

    @Shadow
    protected abstract double getMaxZoom(double startingDistance);

    @Redirect(
        method = "getMaxZoom",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    protected BlockHitResult clipWhenSeated(final BlockGetter instance, final ClipContext context) {
        if (cameraSeated) {
            return RaycastUtilsKt.vanillaClip(instance, context);
        }
        return instance.clip(context);
    }

    @Shadow
    protected abstract void move(double distanceOffset, double verticalOffset, double horizontalOffset);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract Vec3 getPosition();
    // endregion

    @Override
    public void setupWithShipMounted(final @NotNull BlockGetter level, final @NotNull Entity renderViewEntity,
        final boolean thirdPerson, final boolean thirdPersonReverse, final float partialTicks,
        final @NotNull ShipObjectClient shipMountedTo, final @NotNull Vector3dc inShipPlayerPosition) {
        final ShipTransform renderTransform = shipMountedTo.getRenderTransform();
        final Vector3dc playerBasePos =
            renderTransform.getShipToWorldMatrix().transformPosition(inShipPlayerPosition, new Vector3d());
        final Vector3dc originalEyePos = VectorConversionsMCKt.toJOML(getPosition()).sub(playerBasePos);
        final Vector3dc playerEyePos = renderTransform.getShipCoordinatesToWorldCoordinatesRotation()
            .transform(originalEyePos, new Vector3d())
            .add(playerBasePos);

        this.initialized = true;
        this.level = level;
        this.entity = renderViewEntity;
        this.detached = thirdPerson;
        this.mirror = thirdPersonReverse;
        this.setRotationWithShipTransform(renderViewEntity.getViewYRot(partialTicks),
            renderViewEntity.getViewXRot(partialTicks), renderTransform);
        this.setPosition(playerEyePos.x(), playerEyePos.y(), playerEyePos.z());
        if (thirdPerson) {
            if (thirdPersonReverse) {
                this.setRotationWithShipTransform(this.yRot + 180.0F, -this.xRot, renderTransform);
            }

            final AABBi boundingBox = (AABBi) shipMountedTo.getShipVoxelAABB();

            double dist = ((boundingBox.lengthX() + boundingBox.lengthY() + boundingBox.lengthZ()) / 3.0) * 1.5;

            dist = dist > 4 ? dist : 4;

            cameraSeated = true;
            this.move(-this.getMaxZoom(4.0 * (dist / 4.0)), 0.0, 0.0);
            cameraSeated = false;
        }
    }

    @Unique
    private void setRotationWithShipTransform(final float yaw, final float pitch, final ShipTransform renderTransform) {
        final Quaterniondc originalRotation = VectorConversionsMCKt.toJOML(this.rotation);
        final Quaterniondc newRotation =
            renderTransform.getShipCoordinatesToWorldCoordinatesRotation().mul(originalRotation, new Quaterniond());
        this.xRot = pitch;
        this.yRot = yaw;
        VectorConversionsMCKt.set(this.rotation, newRotation);
        this.forwards.set(0.0F, 0.0F, 1.0F);
        this.forwards.transform(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F);
        this.up.transform(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F);
        this.left.transform(this.rotation);
    }
}
