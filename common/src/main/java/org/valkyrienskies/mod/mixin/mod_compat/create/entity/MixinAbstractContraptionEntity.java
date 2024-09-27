package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.simibubi.create.AllMovementBehaviours;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ContraptionWingProvider;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.CreateConversionsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.MixinAbstractContraptionEntityDuck;

@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity extends Entity implements MixinAbstractContraptionEntityDuck,
    ContraptionWingProvider, IEntityDraggingInformationProvider, ShipMountedToDataProvider {

    public MixinAbstractContraptionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    protected abstract StructureTransform makeStructureTransform();

    public StructureTransform getStructureTransform() {
        return this.makeStructureTransform();
    }

    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Clockwork.MixinAbstractContraptionEntity");

    @Unique
    private int wingGroupId = -1;

    @Shadow(remap = false)
    protected Contraption contraption;

    @Shadow
    public abstract Vec3 getPassengerPosition(Entity passenger, float partialTicks);

    @Shadow
    public abstract Vec3 applyRotation(Vec3 localPos, float partialTicks);

    @Shadow
    public abstract Vec3 getAnchorVec();

    @Shadow
    public abstract Vec3 getPrevAnchorVec();

    @Nullable
    @Override
    public ShipMountedToData provideShipMountedToData(@NotNull final Entity passenger, @Nullable final Float partialTicks) {
        final LoadedShip shipObjectEntityMountedTo = VSGameUtilsKt.getShipObjectManagingPos(passenger.level, toJOML(this.position()));
        if (shipObjectEntityMountedTo == null) return null;

        final Vector3dc mountedPosInShip = toJOML(this.getPassengerPosition(passenger, partialTicks == null ? 1 : partialTicks));
        return new ShipMountedToData(shipObjectEntityMountedTo, mountedPosInShip);
    }

    //Region start - fix being sent to the  ̶s̶h̶a̶d̶o̶w̶r̶e̶a̶l̶m̶ shipyard on ship contraption disassembly
    @Redirect(method = "moveCollidedEntitiesOnDisassembly", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        Vec3 result = CompatUtil.INSTANCE.toSameSpaceAs(instance.getCommandSenderWorld(), x, y, z, instance.position());
        if (instance.position().distanceTo(result) < 20) {
            instance.setPos(result.x, result.y, result.z);
        } else LOGGER.warn("Warning distance too high ignoring setPos request");
    }

    @Redirect(method = "moveCollidedEntitiesOnDisassembly", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(DDD)V"))
    private void redirectTeleportTo(Entity instance, double x, double y, double z) {
        Vec3 result = CompatUtil.INSTANCE.toSameSpaceAs(instance.getCommandSenderWorld(), x, y, z, instance.position());
        if (instance.position().distanceTo(result) < 20) {
            if (VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), result.x, result.y, result.z) && instance instanceof AbstractMinecart) {
                result.add(0, 0.5, 0);
            }
            instance.teleportTo(result.x, result.y, result.z);
        } else {
            LOGGER.warn("Warning distance too high ignoring teleportTo request");
        }
    }

    @Inject(method = "toGlobalVector(Lnet/minecraft/world/phys/Vec3;FZ)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private void redirectToGlobalVector(Vec3 localVec, final float partialTicks, final boolean prevAnchor, final CallbackInfoReturnable<Vec3> cir) {
        if (partialTicks != 1 && !prevAnchor) {
            final Vec3 anchor = getAnchorVec();
            final Vec3 oldAnchor = getPrevAnchorVec();
            final Vec3 lerpedAnchor =
                    new Vec3(
                            Mth.lerp(partialTicks, oldAnchor.x, anchor.x),
                            Mth.lerp(partialTicks, oldAnchor.y, anchor.y),
                            Mth.lerp(partialTicks, oldAnchor.z, anchor.z)
                    );
            final Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.ZERO);
            localVec = localVec.subtract(rotationOffset);
            localVec = applyRotation(localVec, partialTicks);
            localVec = localVec.add(rotationOffset)
                    .add(lerpedAnchor);
            cir.setReturnValue(localVec);
        }
    }

    //Region end
    //Region start - Ship contraption actors affecting world
    @Shadow
    public abstract Vec3 toGlobalVector(Vec3 localVec, float partialTicks);

    @Shadow
    public abstract Vec3 getPrevPositionVec();

    @Unique
    private boolean vs$shouldMod(final MovementBehaviour moveBehaviour) {
        return ((moveBehaviour instanceof BlockBreakingMovementBehaviour) || (moveBehaviour instanceof HarvesterMovementBehaviour) || (moveBehaviour instanceof DeployerMovementBehaviour));
    }

    @Unique
    private BlockPos vs$getTargetPos(final MovementBehaviour instance, final MovementContext context, final BlockPos pos, final Vec3 actorPosition) {
        if (vs$shouldMod(instance) && context.world.getBlockState(pos).isAir() && VSGameUtilsKt.isBlockInShipyard(context.world, pos)) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(context.world, pos);
            if (ship != null) {
                final Vector3dc actorPosInWorld = ship.getTransform().getShipToWorld().transformPosition(toJOML(actorPosition));
                return BlockPos.containing(actorPosInWorld.x(), actorPosInWorld.y(), actorPosInWorld.z());
            }
        }
        return pos;
    }

    @Unique
    private boolean vs$forceStall = false;

    @Shadow
    private boolean skipActorStop;

    @Shadow
    @Final
    private static EntityDataAccessor<Boolean> STALLED;

    @Shadow
    public abstract boolean isStalled();

    @Shadow
    protected abstract boolean shouldActorTrigger(MovementContext context, StructureBlockInfo blockInfo, MovementBehaviour actor, Vec3 actorPosition, BlockPos gridPosition);

    @Shadow
    protected abstract boolean isActorActive(MovementContext context, MovementBehaviour actor);

    @Shadow
    protected abstract void onContraptionStalled();

    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false)
    private void preTickActors(final CallbackInfo ci) {
        ci.cancel();

        final boolean stalledPreviously = contraption.stalled;

        if (!level().isClientSide)
            contraption.stalled = vs$forceStall;

        skipActorStop = true;
        for (final MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
            final MovementContext context = pair.right;
            final StructureBlockInfo blockInfo = pair.left;
            final MovementBehaviour actor = AllMovementBehaviours.getBehaviour(blockInfo.state());

            if (actor == null)
                continue;

            final Vec3 oldMotion = context.motion;
            final Vec3 actorPosition = toGlobalVector(VecHelper.getCenterOf(blockInfo.pos())
                .add(actor.getActiveAreaOffset(context)), 1);
            final BlockPos gridPosition = vs$getTargetPos(actor, context, BlockPos.containing(actorPosition), actorPosition); // BlockPos.containing(actorPosition);
            final boolean newPosVisited =
                !context.stall && shouldActorTrigger(context, blockInfo, actor, actorPosition, gridPosition);

            context.rotation = v -> applyRotation(v, 1);
            context.position = actorPosition;
            if (!isActorActive(context, actor) && !actor.mustTickWhileDisabled())
                continue;
            if (newPosVisited && !context.stall) {
                actor.visitNewPosition(context, gridPosition);
                if (!isAlive())
                    break;
                context.firstMovement = false;
            }
            if (!oldMotion.equals(context.motion)) {
                actor.onSpeedChanged(context, oldMotion, context.motion);
                if (!isAlive())
                    break;
            }
            actor.tick(context);
            if (!isAlive())
                break;
            contraption.stalled |= context.stall;
        }
        if (!isAlive()) {
            contraption.stop(level());
            return;
        }
        skipActorStop = false;

        for (final Entity entity : getPassengers()) {
            if (!(entity instanceof final OrientedContraptionEntity orientedCE))
                continue;
            if (contraption.getBearingPosOf(entity.getUUID()) == null)
                continue;
            if (orientedCE.getContraption() != null && orientedCE.getContraption().stalled) {
                contraption.stalled = true;
                break;
            }
        }

        if (!level().isClientSide) {
            if (!stalledPreviously && contraption.stalled)
                onContraptionStalled();
            entityData.set(STALLED, contraption.stalled);
            return;
        }

        contraption.stalled = isStalled();
    }

    @Override
    public void vs$setForceStall(final boolean forceStall) {
        this.vs$forceStall = forceStall;
    }

    //Region end
    //Region start - Contraption Entity Collision
    @Inject(method = "getContactPointMotion", at = @At("HEAD"))
    private void modGetContactPointMotion(Vec3 globalContactPoint, CallbackInfoReturnable<Vec3> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(level(), getAnchorVec().x, getAnchorVec().y, getAnchorVec().z) != VSGameUtilsKt.isBlockInShipyard(level(), getPrevAnchorVec().x, getPrevAnchorVec().y, getPrevAnchorVec().z)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(level(), getAnchorVec());
            if (ship != null) {
                Vec3 result = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(getPrevPositionVec())));
                xo = result.x;
                yo = result.y;
                zo = result.z;
            }
        }
    }
    //Region end

    @Override
    public int getWingGroupId() {
        return wingGroupId;
    }

    @Override
    public void setWingGroupId(final int wingGroupId) {
        this.wingGroupId = wingGroupId;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void postTick(final CallbackInfo ci) {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Level level = thisAsAbstractContraptionEntity.level();
        if (wingGroupId != -1 && level instanceof final ServerLevel serverLevel) {
            final LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(serverLevel,
                VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.position()));
            if (ship != null) {
                try {
                    // This can happen if a player moves a train contraption from ship to world using a wrench
                    ship.getAttachment(WingManager.class)
                        .setWingGroupTransform(wingGroupId, computeContraptionWingTransform());
                } catch (final Exception e) {
                    // I'm not sure why, but this fails sometimes. For now just catch the error and print it
                    e.printStackTrace();
                }
            }
        }
    }

    @NotNull
    @Override
    public Matrix4dc computeContraptionWingTransform() {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Matrix3d rotationMatrix = CreateConversionsKt.toJOML(thisAsAbstractContraptionEntity.getRotationState().asMatrix());
        final Vector3d pos = VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.getAnchorVec());
        return new Matrix4d(rotationMatrix).setTranslation(pos);
    }

    @Override
    public boolean vs$shouldDrag() {
        return false;
    }
}
