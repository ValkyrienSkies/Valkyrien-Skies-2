package org.valkyrienskies.mod.mixin.mod_compat.common_create.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
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
import org.valkyrienskies.mod.compat.CreateCompat;
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
        final LoadedShip shipObjectEntityMountedTo = VSGameUtilsKt.getShipObjectManagingPos(passenger.level(), toJOML(this.position()));
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
            final Vec3 rotationOffset = CreateCompat.getCenterOf(BlockPos.ZERO);
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
    private boolean vs$shouldMod(final Object moveBehaviour) {
        return ((moveBehaviour instanceof BlockBreakingMovementBehaviour) || (moveBehaviour instanceof HarvesterMovementBehaviour) || (moveBehaviour instanceof DeployerMovementBehaviour));
    }

    @Unique
    private BlockPos vs$getTargetPos(final Object moveBehaviour, final MovementContext context, final BlockPos pos, final Vec3 actorPosition) {
        if (vs$shouldMod(moveBehaviour) && context.world.getBlockState(pos).isAir() && VSGameUtilsKt.isBlockInShipyard(context.world, pos)) {
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

    @Redirect(
        method = "tickActors",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/content/contraptions/Contraption;stalled:Z",
            opcode = Opcodes.PUTFIELD
        ),
        remap = false
    )
    private void putVSStall(Contraption contraption, boolean stall) {
        contraption.stalled = vs$forceStall;
    }

    @Unique
    private Object vs$tempActor;
    @Unique
    private Vec3 vs$tempActorActiveAreaOffset;

    @WrapOperation(
        method = "tickActors",
        at = {
            @At( // Create v0.5.1
                value = "INVOKE",
                target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementBehaviour;getActiveAreaOffset(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)Lnet/minecraft/world/phys/Vec3;"
            ),
            @At( // Create v6
                value = "INVOKE",
                target = "Lcom/simibubi/create/api/behaviour/movement/MovementBehaviour;getActiveAreaOffset(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;)Lnet/minecraft/world/phys/Vec3;"
            )
        },
        require = 0,
        remap = false
    )
    private Vec3 stealActor(@Coerce Object actor, MovementContext context, Operation<Vec3> original) {
        Vec3 result = original.call(actor, context);
        vs$tempActor = actor;
        vs$tempActorActiveAreaOffset = result;
        return result;
    }

    @WrapOperation(
        method = "tickActors",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toGlobalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"
        ),
        remap = false
    )
    private Vec3 changeActorPosition(
        AbstractContraptionEntity instance, Vec3 localVec, float partialTicks, Operation<Vec3> original,
        @Local final MovementContext context, @Local final StructureBlockInfo blockInfo
    ) {
        return toGlobalVector(CreateCompat.getCenterOf(blockInfo.pos()).add(vs$tempActorActiveAreaOffset), 1);
    }

    @WrapOperation(
        method = "tickActors",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;containing(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos changeTargetPos(
        Position position, Operation<BlockPos> original,
        @Local final MovementContext context, @Local(ordinal = 1) final Vec3 actorPosition
    ) {
        return vs$getTargetPos(vs$tempActor, context, BlockPos.containing(actorPosition), actorPosition);
    }

    @Override
    public void vs$setForceStall(final boolean forceStall) {
        this.vs$forceStall = forceStall;
    }

    //Region end
    //Region start - Contraption Entity Collision
    @Inject(method = "getContactPointMotion", at = @At("HEAD"), remap = false)
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
        final AbstractContraptionEntity thisAsACE = AbstractContraptionEntity.class.cast(this);
        final Level level = thisAsACE.level();
        if (wingGroupId != -1 && level instanceof final ServerLevel serverLevel) {
            final LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(serverLevel,
                VectorConversionsMCKt.toJOML(thisAsACE.position()));
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
        final AbstractContraptionEntity thisAsACE = AbstractContraptionEntity.class.cast(this);
        final Matrix3d rotationMatrix = CreateConversionsKt.toJOML(thisAsACE.getRotationState().asMatrix());
        final Vector3d pos = VectorConversionsMCKt.toJOML(thisAsACE.getAnchorVec());
        return new Matrix4d(rotationMatrix).setTranslation(pos);
    }

    @Override
    public boolean vs$shouldDrag() {
        return false;
    }
}
