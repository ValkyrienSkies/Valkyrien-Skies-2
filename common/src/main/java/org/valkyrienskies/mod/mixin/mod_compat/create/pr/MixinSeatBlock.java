package org.valkyrienskies.mod.mixin.mod_compat.create.pr;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(SeatBlock.class)
public abstract class MixinSeatBlock extends Block {
    public MixinSeatBlock(final Properties properties) {
        super(properties);
    }

    /**
     * @author ewoudje
     * @reason Floating point additions help
     */
    @Overwrite(remap = false)
    public static void sitDown(final Level world, final BlockPos pos, final Entity entity) {
        if (world.isClientSide)
            return;
        final SeatEntity seat = new SeatEntity(world, pos);
        seat.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        world.addFreshEntity(seat);
        entity.startRiding(seat, true);
        if (entity instanceof final TamableAnimal ta)
            ta.setInSittingPose(true);
    }

    @Shadow
    public static boolean canBePickedUp(final Entity passenger) {
        throw new IllegalStateException();
    }

    @Shadow
    public static boolean isSeatOccupied(final Level world, final BlockPos pos) {
        throw new IllegalStateException();
    }

    /**
     * @author Triode
     * @reason Fix entities not sitting in seats on ships
     */
    @Overwrite
    public void updateEntityAfterFallOn(final @NotNull BlockGetter reader, final Entity entity) {
        BlockPos pos = entity.blockPosition();
        final Long shipStandingOnId = ((IEntityDraggingInformationProvider) entity).getDraggingInformation().getLastShipStoodOn();
        if (shipStandingOnId != null) {
            final Ship ship = VSGameUtilsKt.getShipObjectWorld(entity.level).getLoadedShips().getById(shipStandingOnId);
            if (ship != null) {
                final Vector3dc posInShip = ship.getTransform().getWorldToShip().transformPosition(entity.getX(), entity.getY(), entity.getZ(), new Vector3d());
                pos = new BlockPos(posInShip.x(), posInShip.y(), posInShip.z());
            }
        }
        if (entity instanceof Player || !(entity instanceof LivingEntity) || !canBePickedUp(entity) || isSeatOccupied(entity.level, pos)) {
            if (entity.isSuppressingBounce()) {
                super.updateEntityAfterFallOn(reader, entity);
                return;
            }

            final Vec3 vec3 = entity.getDeltaMovement();
            if (vec3.y < 0.0D) {
                final double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;
                entity.setDeltaMovement(vec3.x, -vec3.y * (double) 0.66F * d0, vec3.z);
            }

            return;
        }
        if (reader.getBlockState(pos).getBlock() != this) {
            return;
        }
        sitDown(entity.level, pos, entity);
    }
}
