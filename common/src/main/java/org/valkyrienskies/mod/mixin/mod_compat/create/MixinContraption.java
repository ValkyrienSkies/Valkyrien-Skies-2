package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.bodies.shape.VoxelBodyShapeData;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.create.ContraptionSegmentHelper;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.MixinAbstractContraptionEntityDuck;

@Mixin(Contraption.class)
public class MixinContraption {
    @Redirect(method = "onEntityCreated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean wrapOp(Level level, Entity entity) {
        // BlockPos anchor = blockFace.getConnectedPos();
        // movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
        //
        // Derive anchor from the code above
        final BlockPos anchor = BlockPos.containing((int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()));
        boolean added = level.addFreshEntity(entity);
        if (added) {
            entity.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        }
        return added;
    }

    @Inject(method = "onEntityCreated", at = @At("HEAD"), remap = false)
    private void preOnEntityCreated(AbstractContraptionEntity entity, CallbackInfo ci) {
        Level level = entity.level();
        if (level.isClientSide) {
            return;
        }

        if (!VSGameConfig.SERVER.getCreate().getEnableContraptionCollisions()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        ServerShipWorld serverShipWorld = ValkyrienSkies.getShipWorld(serverLevel.getServer());
        VsiServerShipWorld vsServerShipWorld = (VsiServerShipWorld) serverShipWorld;

        long bodyId = vsServerShipWorld.getDimensionToGroundBodyIdImmutable().get(ValkyrienSkies.getDimensionId(serverLevel));

        Ship ship = ContraptionSegmentHelper.getContraptionAnchorShip(serverLevel, entity);

        if (ship != null && ship.getBodyId() != null && ship.getBodyId() != bodyId) {
            bodyId = ship.getBodyId();
        }

        boolean isWorld = bodyId == vsServerShipWorld.getDimensionToGroundBodyIdImmutable().get(ValkyrienSkies.getDimensionId(serverLevel));

        long prevId = ((MixinAbstractContraptionEntityDuck) entity).vs$getSegmentId();
        if (prevId != -1) {
            return;
        }

        VoxelBodyShapeData data = ContraptionSegmentHelper.toShapeData(entity);
        if (data == null) {
            return;
        }
        int segmentId = ContraptionSegmentHelper.addContraptionSegment(serverLevel, data, entity, bodyId, isWorld);
        MixinAbstractContraptionEntityDuck duck = (MixinAbstractContraptionEntityDuck) entity;
        duck.vs$setSegmentId(segmentId);
        if (segmentId != -1) {
            duck.vs$setSegmentOwner(bodyId, isWorld);
        }
    }
}
