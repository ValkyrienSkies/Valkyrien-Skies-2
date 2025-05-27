package org.valkyrienskies.mod.forge.mixin.compat.create;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.foundation.collision.Matrix3d;
import javax.annotation.Nullable;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContraptionCollider.class)
public abstract class MixinContraptionCollider {
    @Shadow
    static Vec3 collide(Vec3 p_20273_, Entity e) {
        return p_20273_;
    }

    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Clockwork.MixinContraptionCollider");

    @Unique
    private static AbstractContraptionEntity contraptionEnt;

    @Unique
    private static AABB entityGetBoundingBox(AbstractContraptionEntity abstractContraptionEntity, Entity instance) {
        AABB tempAabb = instance.getBoundingBox();
        if (!VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), instance.blockPosition()) && VSGameUtilsKt.isBlockInShipyard(contraptionEnt.getCommandSenderWorld(), BlockPos.containing(contraptionEnt.getAnchorVec()))) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), contraptionEnt.getAnchorVec());
            if (ship != null) {
                AABBd temp = new AABBd();
                temp.set(toJOML(tempAabb)).transform(ship.getWorldToShip());
                tempAabb = toMinecraft(temp);
            }
        }
        return tempAabb;
    }

    @Unique
    private static Vec3 entityPosition(AbstractContraptionEntity abstractContraptionEntity, Entity instance, boolean old) {
        Vec3 tempVec = old ? new Vec3(instance.xo, instance.yo, instance.zo) : instance.position();
        if (!VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), instance.blockPosition()) && VSGameUtilsKt.isBlockInShipyard(abstractContraptionEntity.getCommandSenderWorld(), BlockPos.containing(abstractContraptionEntity.getAnchorVec()))) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(abstractContraptionEntity.getCommandSenderWorld(), abstractContraptionEntity.getContraption().anchor);
            if (ship != null) {
                Vector3d translatedPos = ship.getTransform().getWorldToShip().transformPosition(toJOML(tempVec));
                tempVec = toMinecraft(translatedPos);
            }
        }
        return tempVec;
    }

    @Unique
    private static Vec3 getSetEntityDeltaMovement(AbstractContraptionEntity abstractContraptionEntity, Entity instance, @Nullable Vec3 motion) {
        Vec3 tempVec = instance.getDeltaMovement();
        if (!VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), instance.blockPosition()) && VSGameUtilsKt.isBlockInShipyard(abstractContraptionEntity.getCommandSenderWorld(), new BlockPos(abstractContraptionEntity.getContraption().anchor))) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), abstractContraptionEntity.getContraption().anchor);
            if (ship != null) {
                if (motion != null) {
                    if (!VSGameUtilsKt.isBlockInShipyard(abstractContraptionEntity.getCommandSenderWorld(), BlockPos.containing(abstractContraptionEntity.getAnchorVec()))) {
                        motion = toMinecraft(ship.getWorldToShip().transformDirection(toJOML(motion)));
                    } else {
                        motion = toMinecraft(ship.getShipToWorld().transformDirection(toJOML(motion)));
                    }
                    instance.setDeltaMovement(motion);
                    motion = null;
                }
                tempVec = toMinecraft(ship.getWorldToShip().transformDirection(toJOML(tempVec)));
            }
        }
        if (motion != null) {
            instance.setDeltaMovement(motion);
        }
        return tempVec;
    }

    @Inject(method = "collideEntities", at = @At("HEAD"), remap = false)
    private static void injectHead(AbstractContraptionEntity contraptionEntity, CallbackInfo ci) {
        contraptionEnt = contraptionEntity;
    }

    private static void warn1(Vector3d vec3) {
        LOGGER.warn("Warning setPosDistance too high ignoring setPos request [" + vec3.x + "," + vec3.y + "," + vec3.z + "]");
    }

    private static void warn2(double x, double y, double z) {
        LOGGER.warn("Warning DEFAULT setPosDistance too high ignoring setPos request [" + x + "," + y + "," + z + "]");
    }

    private static void setOfPos(AbstractContraptionEntity abstractContraptionEntity, Entity instance, double x, double y, double z) {
        if (VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), BlockPos.containing(x, y, z)) &&
                !VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), instance.blockPosition())) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), abstractContraptionEntity.getContraption().anchor);
            if (ship != null) {
                Vector3d newPos = new Vector3d(x, y, z);
                ship.getShipToWorld().transformPosition(newPos, newPos);
                if (instance.position().distanceTo(toMinecraft(newPos)) < 20) {
                    instance.setPos(newPos.x, newPos.y, newPos.z);
                } else
                    warn1(newPos);
            }
        } else {

            if (instance.position().distanceTo(new Vec3(x, y, z)) < 20) {
                instance.setPos(x, y, z);
            } else
                warn2(x, y, z);
        }
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    private static void redirectSetPos(Entity instance, double x, double y, double z) {
        setOfPos(contraptionEnt, instance, x, y, z);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB redirectContraptionGetBoundingBox(AbstractContraptionEntity instance) {
        return VSGameUtilsKt.transformAabbToWorld(instance.getCommandSenderWorld(), instance.getBoundingBox());
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB redirectEntityGetBoundingBox(Entity instance) {
        return entityGetBoundingBox(contraptionEnt, instance);
    }

    /*
    @Redirect(method="collideEntities",at = @At(value="INVOKE_ASSIGN",target = "Lnet/minecraft/world/phys/AABB;getCenter()Lnet/minecraft/world/phys/Vec3;",ordinal = 3))
    private static Vec3 redirectGetCenter(AABB instance){
        if(instance)
        return instance.getCenter();
    }*/

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityPosition(Entity instance) {
        return entityPosition(contraptionEnt, instance, false);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityGetDeltaMovement(Entity instance) {
        return getSetEntityDeltaMovement(contraptionEnt, instance, null);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private static void redirectEntitySetDeltaMovement(Entity instance, Vec3 motion) {
        getSetEntityDeltaMovement(contraptionEnt, instance, motion);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private static double redirectEntityGetX(Entity instance) {
        return entityPosition(contraptionEnt, instance, false).x;
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private static double redirectEntityGetY(Entity instance) {
        return entityPosition(contraptionEnt, instance, false).y;
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private static double redirectEntityGetZ(Entity instance) {
        return entityPosition(contraptionEnt, instance, false).z;
    }


    @Redirect(method = "collideEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;xo:D", opcode = Opcodes.GETFIELD))
    private static double redirectEntityGetXo(Entity instance) {
        return entityPosition(contraptionEnt, instance, true).x;
    }

    @Redirect(method = "collideEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;zo:D", opcode = Opcodes.GETFIELD))
    private static double redirectEntityGetZo(Entity instance) {
        return entityPosition(contraptionEnt, instance, true).z;
    }

    private static Vec3 aaaaaaaaaaaaaa(AbstractContraptionEntity abstractContraptionEntity, Entity entity, Vec3 anchorVec, Matrix3d rotationMatrix, float yawOffset) {

        if (VSGameUtilsKt.isBlockInShipyard(abstractContraptionEntity.getCommandSenderWorld(), abstractContraptionEntity.getContraption().anchor)
                && !VSGameUtilsKt.isBlockInShipyard(abstractContraptionEntity.getCommandSenderWorld(), entity.blockPosition())) {

            Ship ship = VSGameUtilsKt.getShipManagingPos(abstractContraptionEntity.getCommandSenderWorld(), abstractContraptionEntity.getContraption().anchor);
            if (ship != null) {
                Vec3 entityPosition = entityPosition(abstractContraptionEntity, entity, false);
                Vec3 centerY = new Vec3(0, entityGetBoundingBox(contraptionEnt, entity)
                        .getYsize() / 2, 0);
                Vec3 position = entityPosition;
                position = position.add(centerY);
                position = position.subtract(VecHelper.CENTER_OF_ORIGIN);
                position = position.subtract(anchorVec);
                position = VecHelper.rotate(position, -yawOffset, Direction.Axis.Y);
                position = rotationMatrix.transform(position);
                position = position.add(VecHelper.CENTER_OF_ORIGIN);
                position = position.subtract(centerY);
                position = position.subtract(entityPosition);

                return position;//toMinecraft(ship.getShipToWorld().transformPosition(position.x, position.y, position.z, new Vector3d()));
            }
        }
        return ContraptionCollider.getWorldToLocalTranslation(entity, anchorVec, rotationMatrix, yawOffset);
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getPrevPositionVec()Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 redirectGetPrevPositionVec(AbstractContraptionEntity instance) {

        Vec3 prevPos = instance.getPrevPositionVec();

        if (VSGameUtilsKt.isBlockInShipyard(instance.level(), BlockPos.containing(instance.getAnchorVec())) && !VSGameUtilsKt.isBlockInShipyard(instance.level(), BlockPos.containing(instance.getPrevAnchorVec()))) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level(), instance.getAnchorVec());
            if (ship != null) {
                Vec3 result = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(instance.getPrevPositionVec())));
                instance.xo = result.x;
                instance.yo = result.y;
                instance.zo = result.z;
                prevPos = result;
            }
        }


        if (!VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), BlockPos.containing(prevPos)) && VSGameUtilsKt.isBlockInShipyard(instance.getCommandSenderWorld(), BlockPos.containing(instance.position()))) {
            //instance.setOldPosAndRot();
            //prevPos = instance.position();
            /*
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.getCommandSenderWorld(), instance.position());
            if (ship != null) {
                Vec3 transformedPrevPos = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(prevPos)));
                prevPos = transformedPrevPos;
            }*/
        }
        return prevPos;
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;getWorldToLocalTranslation(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/foundation/collision/Matrix3d;F)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 redirectGetWorldToLocalTranslation(Entity entity, Vec3 anchorVec, Matrix3d rotationMatrix, float yawOffset) {
        return aaaaaaaaaaaaaa(contraptionEnt, entity, anchorVec, rotationMatrix, yawOffset);
    }

    @Unique
    private static Vec3 adjustCollide(Vec3 contactPoint, Entity entity) {
        Vec3 result = collide(contactPoint, entity);

        if (VSGameUtilsKt.isBlockInShipyard(entity.getCommandSenderWorld(), contactPoint.x, contactPoint.y, contactPoint.z)
                && !VSGameUtilsKt.isBlockInShipyard(entity.getCommandSenderWorld(), entity.blockPosition())) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(entity.getCommandSenderWorld(), contactPoint);
            if (ship != null) {
                Vec3 temp = toMinecraft(ship.getShipToWorld().transformPosition(toJOML(contactPoint)));
                result = collide(temp, entity);
                result = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(result)));
            }
        } else if (!VSGameUtilsKt.isBlockInShipyard(entity.getCommandSenderWorld(), contactPoint.x, contactPoint.y, contactPoint.z)
                && VSGameUtilsKt.isBlockInShipyard(entity.getCommandSenderWorld(), entity.blockPosition())) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(entity.getCommandSenderWorld(), entity.blockPosition());
            if (ship != null) {
                Vec3 temp = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(contactPoint)));
                result = collide(temp, entity);
                result = toMinecraft(ship.getShipToWorld().transformPosition(toJOML(result)));
            }
        }

        return result;
    }

    @Redirect(method = "collideEntities", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/ContraptionCollider;collide(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private static Vec3 redirectEntityGetBoundingBoxCollide(Vec3 contactPoint, Entity entity) {
        return adjustCollide(contactPoint, entity);
    }

    //@Redirect(method = "getWorldToLocalTranslation(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/foundation/collision/Matrix3d;F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB redirectEntityGetBoundingBox2(Entity instance) {
        return entityGetBoundingBox(contraptionEnt, instance);
    }

    //@Redirect(method = "getWorldToLocalTranslation(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/foundation/collision/Matrix3d;F)Lnet/minecraft/world/phys/Vec3;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityPosition2(Entity instance) {
        return entityPosition(contraptionEnt, instance, false);
    }

    @Inject(method = "worldToLocalPos(Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void modPosition(Vec3 entity, AbstractContraptionEntity contraptionEntity, CallbackInfoReturnable<Vec3> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(contraptionEntity.getCommandSenderWorld(), new BlockPos(contraptionEntity.getContraption().anchor))
                && !VSGameUtilsKt.isBlockInShipyard(contraptionEntity.getCommandSenderWorld(), BlockPos.containing(entity))) {

            Ship ship = VSGameUtilsKt.getShipManagingPos(contraptionEntity.getCommandSenderWorld(), contraptionEntity.getContraption().anchor);
            if (ship != null) {
                cir.setReturnValue(ContraptionCollider.worldToLocalPos(entity, toMinecraft(ship.getShipToWorld().transformPosition(toJOML(contraptionEntity.getAnchorVec()))), contraptionEntity.getRotationState()));
            }
        }
    }

    @Unique
    private static AbstractContraptionEntity hDFTContraptionEntity;

    @ModifyVariable(method = "handleDamageFromTrain", at = @At("HEAD"), argsOnly = true, remap = false)
    private static AbstractContraptionEntity injectHandleDamageFromTrain(AbstractContraptionEntity abstractContraptionEntity) {
        return hDFTContraptionEntity = abstractContraptionEntity;
    }

    @Redirect(method = "handleDamageFromTrain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityGetDeltaMovementFromTrain(Entity instance) {
        return getSetEntityDeltaMovement(hDFTContraptionEntity, instance, null);
    }

    @Unique
    private static AbstractContraptionEntity bounceEntityContraptionEntity;

    @ModifyVariable(method = "bounceEntity", at = @At("HEAD"), argsOnly = true, remap = false)
    private static AbstractContraptionEntity injectBounceEntity(AbstractContraptionEntity abstractContraptionEntity) {
        return bounceEntityContraptionEntity = abstractContraptionEntity;
    }

    @Redirect(method = "bounceEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityPositionBounceEntity(Entity instance) {
        return entityPosition(bounceEntityContraptionEntity, instance, false);
    }

    @Redirect(method = "bounceEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getDeltaMovement()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectEntityGetDeltaMovementBounceEntity(Entity instance) {
        return getSetEntityDeltaMovement(bounceEntityContraptionEntity, instance, null);
    }

    @Redirect(method = "bounceEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private static void redirectEntitySetDeltaMovementBounceEntity(Entity instance, Vec3 motion) {
        getSetEntityDeltaMovement(bounceEntityContraptionEntity, instance, motion);
    }
}
