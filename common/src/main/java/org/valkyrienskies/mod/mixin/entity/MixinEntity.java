package org.valkyrienskies.mod.mixin.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
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
import org.valkyrienskies.core.api.world.ShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    @Unique
    private final EntityDraggingInformation draggingInformation = new EntityDraggingInformation();

    @Unique
    private boolean vs$isInSealedArea = false;
    @Unique
    private BlockPos vs$lastCheckedSealedPos = BlockPos.ZERO;

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    public BlockHitResult addShipsToRaycast(final Level receiver, final ClipContext ctx) {
        return RaycastUtilsKt.clipIncludeShips(receiver, ctx, false);
    }

    //todo: still interact with water on a ship when in a sealed pocket
    @Inject(
        method = "baseTick", at = @At("HEAD")
    )
    private void onBaseTick(CallbackInfo ci) {
        if (this.level == null || this.isRemoved()) {
            return;
        }
        if (!ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) {
            vs$setInSealedArea(false);
            return;
        }

        Entity entity = (Entity) (Object) this;
        Vec3 relativePosition = Vec3.ZERO;
        if (this.getDraggingInformation().isEntityBeingDraggedByAShip()) {
            relativePosition = EntityDragger.INSTANCE.serversidePosition(entity);
        } else {
            final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, null);
            if (shipMountedToData != null) {
                relativePosition = VectorConversionsMCKt.toMinecraft(shipMountedToData.getMountPosInShip()
                    .add(0.0, (double) entity.getEyeHeight(entity.getPose()), 0.0, new Vector3d()));
            }
        }

        boolean isInSealedArea = false;
        final BlockPos relativeBlockPos = BlockPos.containing(relativePosition);
        if (relativePosition != Vec3.ZERO && VSGameUtilsKt.isBlockInShipyard(level, relativeBlockPos)) {
            if (relativeBlockPos.equals(vs$lastCheckedSealedPos)) {
                isInSealedArea = vs$isInSealedArea();
            } else {
                isInSealedArea = VSGameUtilsKt.isPositionSealed(level, relativeBlockPos);
                vs$lastCheckedSealedPos = relativeBlockPos;
            }
        } else if (!VSGameUtilsKt.isBlockInShipyard(level, relativeBlockPos)) {
            final ShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(level);
            for (Ship ship : shipWorld.getAllShips().getIntersecting(
                VectorConversionsMCKt.toJOML(entity.getBoundingBox().inflate(1.0)), VSGameUtilsKt.getDimensionId(level)
            )) {
                relativePosition = VectorConversionsMCKt.toMinecraft(
                    ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(entity.position()), new Vector3d())
                );
                final BlockPos shipRelativeBlockPos = BlockPos.containing(relativePosition);
                if (VSGameUtilsKt.isPositionSealed(level, shipRelativeBlockPos)) {
                    vs$lastCheckedSealedPos = shipRelativeBlockPos;
                    isInSealedArea = true;
                    break;
                }
            }
        }

        vs$setInSealedArea(isInSealedArea);
    }

    @WrapMethod(
        method = "updateFluidOnEyes"
    )
    private void onFluidOnEyes(Operation<Void> original) {
        if (vs$isInSealedArea() && ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) {
            this.wasEyeInWater = false;
            this.fluidOnEyes.clear();
            return;
        }
        original.call();
    }

    @WrapMethod(method = "updateSwimming")
    private void onUpdateSwimming(Operation<Void> original) {
        if (vs$isInSealedArea && ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) {
            this.wasTouchingWater = false;
            this.setSwimming(false);
            return;
        }
        original.call();
    }

    @Inject(
        method = "updateInWaterStateAndDoWaterCurrentPushing",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUpdateInWaterStateAndDoWaterCurrentPushing(CallbackInfo ci) {
        if (vs$isInSealedArea && ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) {
            this.wasTouchingWater = false;
            ci.cancel();
        }
    }

    @WrapMethod(method = "isInBubbleColumn")
    private boolean onIsInBubbleColumn(Operation<Boolean> original) {
        if (vs$isInSealedArea && ValkyrienSkies.isConnectivityEnabled(level.isClientSide)) return false;
        return original.call();
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
            if (!EntityShipCollisionUtils.mayShipIntersectLocalAabb(ship, inShipBB)) {
                continue;
            }
            originalCheckInside(inShipBB);
        }
    }

    @Unique
    private void originalCheckInside(final AABBd aABB) {
        final Entity self = Entity.class.cast(this);
        final BlockPos blockPos = BlockPos.containing(aABB.minX + 0.001, aABB.minY + 0.001, aABB.minZ + 0.001);
        final BlockPos blockPos2 = BlockPos.containing(aABB.maxX - 0.001, aABB.maxY - 0.001, aABB.maxZ - 0.001);
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
            //return;
            if (Entity.class.cast(this) instanceof final Player player && player instanceof final IEntityDraggingInformationProvider dragProvider) {
                if (dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip() && dragProvider.getDraggingInformation().getServerRelativePlayerYaw() != null) {
                    final Ship shipDraggedBy = VSGameUtilsKt.getAllShips(level).getById(dragProvider.getDraggingInformation().getLastShipStoodOn());
                    if (shipDraggedBy != null) {
                        final Vec3 localEyePosition = EntityDragger.INSTANCE.serversideEyePosition(player);
                        if (!VSGameUtilsKt.isBlockInShipyard(level, localEyePosition)) {
                            return;
                        }

                        final ShipTransform shipTransform;
                        if (shipDraggedBy instanceof ClientShip) {
                            shipTransform = ((ClientShip) shipDraggedBy).getRenderTransform();
                        } else {
                            shipTransform = shipDraggedBy.getShipTransform();
                        }
                        final Vec3 worldEyePosition = VectorConversionsMCKt.toMinecraft(
                            shipTransform.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(localEyePosition))
                        );
                        cir.setReturnValue(worldEyePosition);
                    }
                }
            }
            return;
        }
        final LoadedShip shipMountedTo = shipMountedToData.getShipMountedTo();

        final ShipTransform shipTransform;
        if (shipMountedTo instanceof ClientShip) {
            shipTransform = ((ClientShip) shipMountedTo).getRenderTransform();
        } else {
            shipTransform = shipMountedTo.getTransform();
        }
        final Vector3dc basePos = shipTransform.getShipToWorld()
            .transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());
        final Vector3dc eyeRelativePos = shipTransform.getShipToWorldRotation().transform(
            new Vector3d(0.0, getEyeHeight(), 0.0)
        );
        final Vec3 newEyePos = VectorConversionsMCKt.toMinecraft(basePos.add(eyeRelativePos, new Vector3d()));
        cir.setReturnValue(newEyePos);
    }

    /**
     * @reason Needed for players to pick blocks correctly when mounted to a ship
     *
     * Needed, because before we only fixed the clientside one.
     */
    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void preGetEyePositionServer(final CallbackInfoReturnable<Vec3> cir) {
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(Entity.class.cast(this), null);
        if (shipMountedToData == null) {
            return;
        }
        final LoadedShip shipMountedTo = shipMountedToData.getShipMountedTo();

        final ShipTransform shipTransform;
        if (shipMountedTo instanceof ClientShip) {
            shipTransform = ((ClientShip) shipMountedTo).getRenderTransform();
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
     * @reason Without this and that other mixin, things don't render correctly at high speeds.
     * @see org.valkyrienskies.mod.mixin.client.renderer.MixinEntityRenderer
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void onShouldRender(double d, double e, double f, CallbackInfoReturnable<Boolean> cir) {
        if (this.draggingInformation.isEntityBeingDraggedByAShip()) {
            final Ship ship = VSGameUtilsKt.getShipObjectWorld(this.level).getAllShips().getById(this.draggingInformation.getLastShipStoodOn());
            if (ship != null) {
                final ShipTransform shipTransform = (ship instanceof ClientShip ? ((ClientShip) ship).getRenderTransform() : ship.getTransform());
                if (this.draggingInformation.getRelativePositionOnShip() != null) {
                    Vector3dc redir = shipTransform.getShipToWorld().transformPosition(this.draggingInformation.getRelativePositionOnShip(), new Vector3d());
                    double distX = redir.x() - d;
                    double distY = redir.y() - e;
                    double distZ = redir.z() - f;
                    double sqrDist = distX * distX + distY * distY + distZ * distZ;
                    cir.setReturnValue(shouldRenderAtSqrDistance(sqrDist));
                }
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
    public abstract boolean shouldRenderAtSqrDistance(double d);

    @Shadow
    public boolean hasImpulse;

    @Shadow
    public abstract void push(double d, double e, double f);

    @Shadow
    public abstract boolean isRemoved();

    @Shadow
    protected boolean wasTouchingWater;

    @Shadow
    protected boolean wasEyeInWater;

    @Shadow
    @Final
    private Set<TagKey<Fluid>> fluidOnEyes;

    @Shadow
    public abstract void setSwimming(boolean bl);

    @Override
    @NotNull
    public EntityDraggingInformation getDraggingInformation() {
        return draggingInformation;
    }

    @Override
    public boolean vs$shouldDrag() {
        return true;
    }

    @Override
    public boolean vs$isInSealedArea() {
        return vs$isInSealedArea;
    }

    @Override
    public void vs$setInSealedArea(final boolean inSealedArea) {
        this.vs$isInSealedArea = inSealedArea;
    }

    @Override
    public void vs$dragImmediately(Ship ship){
        if(ship == null) return;
        draggingInformation.setLastShipStoodOn(ship.getId());
        draggingInformation.setShouldImpulseMovement(false);
    }
}
