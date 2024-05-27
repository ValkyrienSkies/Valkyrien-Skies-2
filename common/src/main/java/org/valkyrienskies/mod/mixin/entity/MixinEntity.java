package org.valkyrienskies.mod.mixin.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.impl.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    @Unique
    private final EntityDraggingInformation draggingInformation = new EntityDraggingInformation();

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final Level receiver, final ClipContext ctx) {
        return RaycastUtilsKt.clipIncludeShips(receiver, ctx);
    }

    @Inject(
        at = @At("TAIL"),
        method = "checkInsideBlocks"
    )
    private void afterCheckInside(final CallbackInfo ci) {
        final AABBd boundingBox = toJOML(getBoundingBox());
        final AABBd temp = new AABBd();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, boundingBox)) {
            final AABBd inShipBB = boundingBox.transform(ship.getShipTransform().getWorldToShipMatrix(), temp);
            originalCheckInside(inShipBB);
        }
    }

    @Unique
    private void originalCheckInside(final AABBd aABB) {
        final Entity self = Entity.class.cast(this);
        final BlockPos blockPos = new BlockPos(aABB.minX + 0.001, aABB.minY + 0.001, aABB.minZ + 0.001);
        final BlockPos blockPos2 = new BlockPos(aABB.maxX - 0.001, aABB.maxY - 0.001, aABB.maxZ - 0.001);
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        if (this.level.hasChunksAt(blockPos, blockPos2)) {
            for (int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                for (int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                    for (int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                        mutableBlockPos.set(i, j, k);
                        final BlockState blockState = this.level.getBlockState(mutableBlockPos);

                        try {
                            blockState.entityInside(this.level, mutableBlockPos, self);
                            this.onInsideBlock(blockState);
                        } catch (final Throwable var12) {
                            final CrashReport crashReport =
                                CrashReport.forThrowable(var12, "Colliding entity with block");
                            final CrashReportCategory crashReportCategory =
                                crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, this.level, mutableBlockPos,
                                blockState);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     */
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void preGetEyePosition(final float partialTicks, final CallbackInfoReturnable<Vec3> cir) {
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(Entity.class.cast(this), partialTicks);
        if (shipMountedToData == null) {
            return;
        }
        final LoadedShip shipMountedTo = shipMountedToData.getShipMountedTo();

        final ShipTransform shipTransform;
        if (shipMountedTo instanceof ShipObjectClient) {
            shipTransform = ((ShipObjectClient) shipMountedTo).getRenderTransform();
        } else {
            shipTransform = shipMountedTo.getShipTransform();
        }
        final Vector3dc basePos = shipTransform.getShipToWorldMatrix()
            .transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());
        final Vector3dc eyeRelativePos = shipTransform.getShipCoordinatesToWorldCoordinatesRotation().transform(
            new Vector3d(0.0, getEyeHeight(), 0.0)
        );
        final Vec3 newEyePos = VectorConversionsMCKt.toMinecraft(basePos.add(eyeRelativePos, new Vector3d()));
        cir.setReturnValue(newEyePos);
    }

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     */
    @Inject(method = "calculateViewVector", at = @At("HEAD"), cancellable = true)
    private void preCalculateViewVector(final float xRot, final float yRot, final CallbackInfoReturnable<Vec3> cir) {
        final LoadedShip shipMountedTo = VSGameUtilsKt.getShipMountedTo(Entity.class.cast(this));
        if (shipMountedTo == null) {
            return;
        }
        final float f = xRot * (float) (Math.PI / 180.0);
        final float g = -yRot * (float) (Math.PI / 180.0);
        final float h = Mth.cos(g);
        final float i = Mth.sin(g);
        final float j = Mth.cos(f);
        final float k = Mth.sin(f);
        final Vector3dc originalViewVector = new Vector3d(i * j, -k, h * j);

        final ShipTransform shipTransform;
        if (shipMountedTo instanceof ClientShip) {
            shipTransform = ((ClientShip) shipMountedTo).getRenderTransform();
        } else {
            shipTransform = shipMountedTo.getShipTransform();
        }
        final Vec3 newViewVector = VectorConversionsMCKt.toMinecraft(
            shipTransform.getShipCoordinatesToWorldCoordinatesRotation().transform(originalViewVector, new Vector3d()));
        cir.setReturnValue(newViewVector);
    }

    @Inject(method = "touchingUnloadedChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void isTouchingUnloadedChunkOrShip(CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide) {
            ServerLevel slevel = (ServerLevel) level;
            if (VSGameUtilsKt.getShipsIntersecting(slevel, getBoundingBox()).iterator().hasNext()) {
                cir.setReturnValue(true);
            }
        }
    }

    // region shadow functions and fields
    @Shadow
    public Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    protected abstract void onInsideBlock(BlockState state);

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract float getEyeHeight();

    // endregion

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    public abstract Vec3 getPosition(float f);

    @Override
    @NotNull
    public EntityDraggingInformation getDraggingInformation() {
        return draggingInformation;
    }

    @Override
    public boolean vs$shouldDrag() {
        return true;
    }
}
