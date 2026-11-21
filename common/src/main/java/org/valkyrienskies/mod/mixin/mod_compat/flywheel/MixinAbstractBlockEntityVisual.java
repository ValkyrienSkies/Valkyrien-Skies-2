package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;
import org.valkyrienskies.mod.compat.flywheel.ShipEmbeddingManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(AbstractBlockEntityVisual.class)
public class MixinAbstractBlockEntityVisual<T extends BlockEntity> {
    @Shadow
    @Final
    protected T blockEntity;

    @WrapOperation(
        method = "isVisible",
        at = @At(value = "INVOKE", target = "Lorg/joml/FrustumIntersection;testSphere(FFFF)Z"),
        remap = false
    )
    private boolean testRedirected(FrustumIntersection instance, float x, float y, float z, float r,
        Operation<Boolean> original) {
        Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(blockEntity.getLevel(), blockEntity.getBlockPos().getCenter());
        final VisualizationManager manager = VisualizationManager.get(blockEntity.getLevel());
        final Vec3i renderOrigin = manager == null ? Vec3i.ZERO : manager.renderOrigin();
        var pieces = ShipEmbeddingManager.piecesFor(blockEntity);
        if (!pieces.isEmpty()) {
            var center = blockEntity.getBlockPos().getCenter();
            var scale = new Vector3d();
            var projected = new Vector3d();
            for (var piece : pieces) {
                var transform = piece.getShipToWorld();
                transform.transformPosition(center.x, center.y, center.z, projected);
                transform.getScale(scale);
                float radius = (float) (r * Math.max(scale.x, Math.max(scale.y, scale.z)));
                if (original.call(instance, (float)(projected.x - renderOrigin.getX()), (float)(projected.y - renderOrigin.getY()),
                    (float)(projected.z - renderOrigin.getZ()), radius)) return true;
            }
            return false;
        }
        return original.call(instance,
            (float) (worldPos.x - renderOrigin.getX()),
            (float) (worldPos.y - renderOrigin.getY()),
            (float) (worldPos.z - renderOrigin.getZ()),
            r
        );
    }

    @Redirect(
        method = "doDistanceLimitThisFrame",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D")
    )
    private double distInclShips(BlockPos blockPos, Position position, @Local(argsOnly = true) DynamicVisual.Context context){
        var pieces = ShipEmbeddingManager.piecesFor(blockEntity);
        if (!pieces.isEmpty()) {
            double nearest = Double.POSITIVE_INFINITY;
            var projected = new Vector3d();
            for (var piece : pieces) {
                piece.getShipToWorld().transformPosition(
                    blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, projected);
                nearest = Math.min(nearest, projected.distanceSquared(position.x(), position.y(), position.z()));
            }
            return nearest;
        }
        return VSGameUtilsKt.toWorldCoordinates(context.camera().getEntity().level(), blockPos.getCenter()).distanceToSqr(position.x(), position.y(), position.z());
    }
}
