package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(EdgeInteractionHandler.class)
public class MixinEdgeInteractionHandler {
    @WrapOperation(
        method={"Lcom/simibubi/create/foundation/blockEntity/behaviour/edgeInteraction/EdgeInteractionHandler;onBlockActivated(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", "Lcom/simibubi/create/foundation/blockEntity/behaviour/edgeInteraction/EdgeInteractionHandler;onBlockActivated(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock;)V"},
        at=@At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 injectGetLocation(BlockHitResult instance, Operation<Vec3> original,  @Local(ordinal = 0)
    Level world) {
        // BlockHitResult.getLocation returns a world position
        Vec3 originalResult = original.call(instance);

        Ship ship = VSGameUtilsKt.getShipManagingPos(world, instance.getBlockPos());
        if (ship != null) {
            Vector3d resultJoml = VectorConversionsMCKt.toJOML(originalResult);

            // Get it back to the shipyard for the AABB check
            ship.getTransform().getWorldToShip().transformPosition(resultJoml);

            return VectorConversionsMCKt.toMinecraft(resultJoml);
        }
        // No ship, don't do anything
        return originalResult;
    }
}
