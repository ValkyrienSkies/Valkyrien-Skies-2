package org.valkyrienskies.mod.mixin.feature.ai.shulker;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixin.accessors.world.entity.monster.ShulkerAccessor;

// Allow shulker teleports to cross frames (ship→world, ship→other ship, world→ship): seed candidates from the apparent world position; clear stale dragger after success; explicitly migrate to the new attach ship if the chosen attach face lands on a ship-block. Per-candidate validation (isEmptyBlock / canStayAt / noCollision) is covered by MixinShulkerAttach.
@Mixin(Shulker.class)
public abstract class MixinShulkerTeleportFrames {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<AABBd> VS$PROBE = ThreadLocal.withInitial(AABBd::new);

    @WrapOperation(
        method = "teleportSomewhere",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/Shulker;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$worldBaseForTeleportCandidates(
        final Shulker self, final Operation<BlockPos> original
    ) {
        final BlockPos rawPos = original.call(self);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(self.level(), rawPos);
        if (ship == null) return rawPos;
        final Vector3d worldCenter = ship.getTransform().getShipToWorld().transformPosition(
            VS$IN.get().set(rawPos.getX() + 0.5, rawPos.getY() + 0.5, rawPos.getZ() + 0.5),
            VS$OUT.get()
        );
        return BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
    }

    @Inject(method = "teleportSomewhere", at = @At("RETURN"))
    private void vs$migrateOnSuccessfulTeleport(final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        final Shulker self = (Shulker) (Object) this;
        final Level level = self.level();

        // Clear stale dragger info — old attribution would re-migrate the shulker into the OLD ship next tick via MixinShulkerShipyardMigrate.
        if (self instanceof IEntityDraggingInformationProvider provider) {
            provider.getDraggingInformation().setLastShipStoodOn(null);
        }

        // The cell holding the block the shulker is now attached to is one step in attachFace from its new world cell.
        final Direction attachFace = self.getAttachFace();
        final BlockPos attachedCell = self.blockPosition().relative(attachFace);

        final Ship ship = vs$findShipAttachedTo(level, attachedCell);
        if (ship == null) return;  // pure world attach — stay a world entity

        // Re-orient attachFace from world to ship cardinals before migrating: vanilla aiStep next tick calls canStayAt(blockPosition(), getAttachFace()) and reads the cardinal in the entity's frame; without re-orient the ship-local read hits air, canStayAt fails, the shulker re-teleports → bounces back to world frame. Direction.getNearest snaps to the closest cardinal — exact for any 90° ship rotation, approximate otherwise (the shulker keeps re-teleporting until it lands on a candidate whose rounded ship-local face genuinely supports it).
        final Vector3d faceShip = ship.getTransform().getWorldToShip().transformDirection(
            VS$IN.get().set(attachFace.getStepX(), attachFace.getStepY(), attachFace.getStepZ())
        );
        final Direction shipAttachFace = Direction.getNearest(faceShip.x, faceShip.y, faceShip.z);

        DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(self, ship);
        ((ShulkerAccessor) self).vs$setAttachFace(shipAttachFace);
    }

    // For each ship whose worldAABB intersects [worldCell], project the cell center to ship-local and read the block; return the first ship with a non-air block there. WorldAABB-scan as the candidate set + actual block-read confirm — not a raw AABB-overlap call.
    @Unique
    private static Ship vs$findShipAttachedTo(final Level level, final BlockPos worldCell) {
        final Vector3d worldCenter = VS$IN.get().set(
            worldCell.getX() + 0.5, worldCell.getY() + 0.5, worldCell.getZ() + 0.5);
        final AABBd probe = VS$PROBE.get()
            .setMin(worldCell.getX(), worldCell.getY(), worldCell.getZ())
            .setMax(worldCell.getX() + 1.0, worldCell.getY() + 1.0, worldCell.getZ() + 1.0);
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
            final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
                worldCenter, VS$OUT.get()
            );
            final BlockPos shipLocalPos = BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
            final BlockState state = level.getBlockState(shipLocalPos);
            if (!state.isAir()) {
                return ship;
            }
        }
        return null;
    }
}
