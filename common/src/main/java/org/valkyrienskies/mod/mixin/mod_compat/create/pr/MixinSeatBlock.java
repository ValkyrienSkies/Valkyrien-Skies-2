package org.valkyrienskies.mod.mixin.mod_compat.create.pr;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(SeatBlock.class)
public abstract class MixinSeatBlock extends Block {
    public MixinSeatBlock(final Properties properties) {
        super(properties);
    }

    /**
     * Use doubles instead of floats when invoking setPos()
     */
    @WrapOperation(method = "sitDown", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/actors/seat/SeatEntity;setPos(DDD)V"))
    private static void wrapSitDownSetPos(final SeatEntity seatEntity, final double origX, final double origY, final double origZ, final Operation<Void> setPosOperation, final Level world, final BlockPos pos, final Entity entity) {
        setPosOperation.call(seatEntity, pos.getX() + .5, (double) pos.getY(), pos.getZ() + .5);
    }

    /**
     * @author Triode
     * @reason Fix entities not sitting in seats on ships
     */
    @WrapOperation(
        method = "updateEntityAfterFallOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
        ),
        remap = false
    )
    private BlockPos wrapBlockPosInUpdateEntityAfterFallOn(
        final Entity entity,
        final Operation<BlockPos> getBlockPosition
    ) {
        final EntityDraggingInformation draggingInformation = ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
        if (draggingInformation.isEntityBeingDraggedByAShip()) {
            final Long shipStandingOnId = draggingInformation.getLastShipStoodOn();
            if (shipStandingOnId != null) {
                final Ship ship = VSGameUtilsKt.getShipObjectWorld(entity.level).getLoadedShips().getById(shipStandingOnId);
                if (ship != null) {
                    final Vector3dc posInShip = ship.getTransform().getWorldToShip()
                        .transformPosition(entity.getX(), entity.getY(), entity.getZ(), new Vector3d());
                    return new BlockPos(posInShip.x(), posInShip.y(), posInShip.z());
                }
            }
        }
        return getBlockPosition.call(entity);
    }
}
