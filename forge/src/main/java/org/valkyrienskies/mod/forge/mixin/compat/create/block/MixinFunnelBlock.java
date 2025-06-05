package org.valkyrienskies.mod.forge.mixin.compat.create.block;

import static com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock.getFunnelFacing;

import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(FunnelBlock.class)
public class MixinFunnelBlock {


    @ModifyVariable(
        method= "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "STORE")
    )
    public Vec3 entityInside(Vec3 diff, BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(worldIn, pos);
        Direction direction = getFunnelFacing(state);

        if (ship != null) {
            diff = VectorConversionsMCKt.toMinecraft(
                ship.getTransform().getWorldToShip().transformPosition(
                    VectorConversionsMCKt.toJOML(
                        entityIn.position()
                    )
                )
            ).subtract(VecHelper.getCenterOf(pos)
                    .add(Vec3.atLowerCornerOf(direction.getNormal())
                        .scale(-.325f)));
        }
        return diff;
    }
}
