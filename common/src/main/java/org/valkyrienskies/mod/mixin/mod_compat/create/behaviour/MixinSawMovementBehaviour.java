package org.valkyrienskies.mod.mixin.mod_compat.create.behaviour;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SawMovementBehaviour.class)
public abstract class MixinSawMovementBehaviour extends BlockBreakingMovementBehaviour {
    @WrapMethod(
        method = "dropItemFromCutTree"
    )
    private void shipCutTree(MovementContext context, BlockPos pos, ItemStack stack, Operation<Void> original){
        final Ship cutTreeShip = VSGameUtilsKt.getShipManagingPos(context.world, pos);
        final Ship sawShip = VSGameUtilsKt.getShipManagingPos(context.world, context.position);
        final Vector3d treePos = VectorConversionsMCKt.toJOML(Vec3.atCenterOf(pos));
        if (cutTreeShip == null && sawShip == null) {
            original.call(context, pos, stack);
            return;
        }
        if (cutTreeShip != null) cutTreeShip.getShipToWorld().transformPosition(treePos);
        if (sawShip != null) sawShip.getWorldToShip().transformPosition(treePos);
        original.call(context, BlockPos.containing(treePos.x, treePos.y, treePos.z), stack);
    }
}
