package org.valkyrienskies.mod.mixin.mod_compat.create.pr;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SeatBlock.class)
public class MixinSeatBlock {

    /**
     * @author ewoudje
     * @reason Floating point additions help
     */
    @Overwrite(remap = false)
    public static void sitDown(Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide)
            return;
        SeatEntity seat = new SeatEntity(world, pos);
        seat.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        world.addFreshEntity(seat);
        entity.startRiding(seat, true);
        if (entity instanceof TamableAnimal ta)
            ta.setInSittingPose(true);
    }
}
