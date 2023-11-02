package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerMovementBehaviour;
import com.simibubi.create.foundation.utility.VecHelper;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ContraptionWingProvider;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.CreateConversionsKt;
import org.valkyrienskies.mod.mixin.mod_compat.create.IMixinDeployerHandler;
import org.valkyrienskies.mod.mixin.mod_compat.create.IMixinDeployerMovementBehaviour;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.MixinAbstractContraptionEntityDuck;

@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity extends Entity implements MixinAbstractContraptionEntityDuck,
    ContraptionWingProvider {

    public MixinAbstractContraptionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
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

    //Region end
    //Region start - fix entity rider position on ship contraptions
    @Override
    public void positionRider(@NotNull Entity passenger) {
        if (!hasPassenger(passenger))
            return;
        Vec3 riderPos = getPassengerPosition(passenger, 1);
        if (riderPos == null)
            return;
        if (!(passenger instanceof OrientedContraptionEntity)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(passenger.level, riderPos.x, riderPos.y, riderPos.z);
            riderPos.add(0, SeatEntity.getCustomEntitySeatOffset(passenger) - 1 / 8f, 0);
            if (ship != null) {
                riderPos = toMinecraft(ship.getShipToWorld().transformPosition(toJOML(riderPos)));
            }
        }
        passenger.setPos(riderPos);
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

    @Shadow
    protected abstract StructureTransform makeStructureTransform();

    @Unique
    private static Boolean isValidBlock(Level world, BlockPos colliderPos, MovementBehaviour movementBehaviour, MovementContext context) {
        BlockState worldBlockState = world.getBlockState(colliderPos);
        if (worldBlockState.getBlock() instanceof CocoaBlock)
            return false;
        if (movementBehaviour != null) {
            if (movementBehaviour instanceof DeployerMovementBehaviour && context.temporaryData instanceof DeployerFakePlayer player) {
                ((IMixinDeployerMovementBehaviour) movementBehaviour).invokeTryGrabbingItem(context);
                if ((player.getMainHandItem().getItem() instanceof BlockItem blockItem) && (blockItem.getBlock() instanceof CropBlock)) {
                    return IMixinDeployerHandler.invokeShouldActivate(player.getMainHandItem(), context.world, colliderPos, null);
                } else
                    return false;
            }
            if (movementBehaviour instanceof PloughMovementBehaviour behaviour) {
                return worldBlockState.isAir() || behaviour.canBreak(world, colliderPos, worldBlockState);
            }
            if (movementBehaviour instanceof HarvesterMovementBehaviour harvesterMovementBehaviour) {
                return harvesterMovementBehaviour.isValidCrop(world, colliderPos, worldBlockState)
                        || harvesterMovementBehaviour.isValidOther(world, colliderPos, worldBlockState);
            }
            if (movementBehaviour instanceof BlockBreakingMovementBehaviour behaviour) {
                return behaviour.canBreak(world, colliderPos, worldBlockState);
            }
        }
        return !worldBlockState.isAir();
    }

    @Unique
    private boolean shouldMod(MovementBehaviour moveBehaviour) {
        return ((moveBehaviour instanceof BlockBreakingMovementBehaviour) || (moveBehaviour instanceof HarvesterMovementBehaviour) || (moveBehaviour instanceof DeployerMovementBehaviour));
    }

    @Unique
    private StructureTemplate.StructureBlockInfo structureBlockInfo;

    @Inject(method = "tickActors", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/AllMovementBehaviours;getBehaviour(Lnet/minecraft/world/level/block/state/BlockState;)Lcom/simibubi/create/content/contraptions/behaviour/MovementBehaviour;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void injectTickActors(CallbackInfo ci, boolean stalledPreviously, Iterator<MutablePair<StructureTemplate.StructureBlockInfo, MovementContext>> var2, MutablePair<StructureTemplate.StructureBlockInfo, MovementContext> pair, MovementContext context, StructureTemplate.StructureBlockInfo blockInfo) {
        structureBlockInfo = blockInfo;
    }

    @Unique
    private BlockPos getTargetPos(MovementBehaviour instance, MovementContext context, BlockPos pos, StructureTemplate.StructureBlockInfo blockInfo) {
        if (shouldMod(instance) && context.world.getBlockState(pos).isAir() && VSGameUtilsKt.isBlockInShipyard(context.world, pos)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(context.world, pos);
            if (ship != null) {
                Vector3d searchPos = toJOML(toGlobalVector(VecHelper.getCenterOf(blockInfo.pos)
                        .add(instance.getActiveAreaOffset(context)), 1));
                Vector3d searchPos2 = new Vector3d(searchPos);

                Vec3 transformedSearchPos = toMinecraft(ship.getShipToWorld().transformPosition(searchPos));
                if (blockInfo.state.hasProperty(DirectionalBlock.FACING)) {
                    searchPos2.add(toJOML(
                            Vec3.atLowerCornerOf(blockInfo.state.getValue(DirectionalBlock.FACING).getNormal())
                    ));
                } else {
                    searchPos2.add(0, 0, 1);
                }
                Vec3 transformedSearchPos2 = toMinecraft(ship.getShipToWorld().transformPosition(searchPos2));
                BlockPos blockPos = new BlockPos(transformedSearchPos);
                boolean check = isValidBlock(context.world, blockPos, instance, context);
                if (level.isClientSide) {
                    //Debug helper for future mixin work
                    //boolean debugView = Minecraft.getInstance().options.renderDebug;
                    if (false) {
                        int ttl = 100;
                        CreateClient.OUTLINER.showLine("actorPosDebugLine-" + context.localPos + "-" + context.contraption.anchor, transformedSearchPos, transformedSearchPos2)
                                .colored(0x0070ff)
                                .lineWidth(2 / 32f);
                        CreateClient.OUTLINER.showAABB("actorPosDebug-" + context.localPos + "-" + context.contraption.anchor, new AABB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1), ttl)
                                .colored(check ? 0xff7000 : 0x70ff00)
                                .lineWidth(2 / 16f);
                    }
                }
                if (check)
                    pos = blockPos;
            }
        }
        return pos;
    }

    @Redirect(method = "tickActors", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/behaviour/MovementBehaviour;visitNewPosition(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V"))
    private void redirectVisitNewPosition(MovementBehaviour instance, MovementContext context, BlockPos pos) {
        instance.visitNewPosition(context, getTargetPos(instance, context, pos, structureBlockInfo));
    }

    //Region end
    //Region start - Contraption Entity Collision
    @Inject(method = "getContactPointMotion", at = @At("HEAD"))
    private void modGetContactPointMotion(Vec3 globalContactPoint, CallbackInfoReturnable<Vec3> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(level, getAnchorVec().x, getAnchorVec().y, getAnchorVec().z) != VSGameUtilsKt.isBlockInShipyard(level, getPrevAnchorVec().x, getPrevAnchorVec().y, getPrevAnchorVec().z)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, getAnchorVec());
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
    public StructureTransform getStructureTransform() {
        return makeStructureTransform();
    }

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
        final Level level = thisAsAbstractContraptionEntity.level;
        if (wingGroupId != -1 && level instanceof final ServerLevel serverLevel) {
            final LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(serverLevel,
                VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.position()));
            if (ship != null) {
                // This can happen if a player moves a train contraption from ship to world using a wrench
                ship.getAttachment(WingManager.class).setWingGroupTransform(wingGroupId, computeContraptionWingTransform());
            }
        }
    }

    @NotNull
    @Override
    public Matrix4dc computeContraptionWingTransform() {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Matrix3d rotationMatrix =
            CreateConversionsKt.toJOML(thisAsAbstractContraptionEntity.getRotationState().asMatrix());
        final Vector3d pos = VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.getAnchorVec());
        return new Matrix4d(rotationMatrix).setTranslation(pos);
    }
}
