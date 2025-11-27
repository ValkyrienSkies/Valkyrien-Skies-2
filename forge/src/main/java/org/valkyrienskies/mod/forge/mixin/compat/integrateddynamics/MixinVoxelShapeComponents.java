package org.valkyrienskies.mod.forge.mixin.compat.integrateddynamics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.cyclops.integrateddynamics.core.block.BlockRayTraceResultComponent;
import org.cyclops.integrateddynamics.core.block.VoxelShapeComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This fixes Integrated Dynamics blocks not working on ships. Integrated Dynamics uses a custom raytrace
 * for its blocks which are smaller than a full cube in size. This ensures that those raytraces are done in the shipyard.
 * <p>
 * Note: we already have mixins that do this for the vanilla clip method, but ID made its own clip method that does only one block.
 *
 * @see <a href="https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/218">Issue #218</a>
 */
@Mixin(VoxelShapeComponents.class)
public class MixinVoxelShapeComponents {

    @WrapOperation(
        method = "Lorg/cyclops/integrateddynamics/core/block/VoxelShapeComponents;rayTrace(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Lorg/cyclops/integrateddynamics/core/block/BlockRayTraceResultComponent;",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lorg/cyclops/integrateddynamics/core/block/VoxelShapeComponents;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Lorg/cyclops/integrateddynamics/core/block/BlockRayTraceResultComponent;",
            remap = true
        )
    )
    public BlockRayTraceResultComponent preRaytrace(final VoxelShapeComponents instance, final Vec3 startVec, final Vec3 endVec,
        final BlockPos entry, final Operation<BlockRayTraceResultComponent> original, final BlockPos pos, @Nullable final Entity entity) {

        // If we're raytracing from an entity (the player), and the player is looking at an Integrated Dynamics block
        // in the shipyard, we transform the start and endpoints of the raytrace into the shipyard so that it works
        // properly.
        if (entity != null) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(entity.getCommandSenderWorld(), pos);
            if (ship != null) {
                final Vec3 newStart = VectorConversionsMCKt.transformPosition(ship.getWorldToShip(), startVec);
                final Vec3 newEnd = VectorConversionsMCKt.transformPosition(ship.getWorldToShip(), endVec);

                return original.call(instance, newStart, newEnd, pos);
            }
        }

        // otherwise just default to the original behavior
        return original.call(instance, startVec, endVec, pos);
    }

}
