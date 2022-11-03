package org.valkyrienskies.mod.mixin.feature.render_blockentity_distance_check;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This mixin fixes {@link BlockEntity}s belonging to ships not rendering.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    @Shadow
    public Level level;

    @Shadow
    public Camera camera;

    /**
     * This mixin fixes the culling of {@link BlockEntity}s that belong to a ship.
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"
        )
    )
    private <E extends BlockEntity> boolean isTileEntityInRenderRange(final BlockEntityRenderer<E> instance,
        final E methodBlockEntity,
        final Vec3 tileEntityPos) {

        final boolean defaultResult = instance.shouldRender(methodBlockEntity, tileEntityPos);
        if (defaultResult) {
            return true;
        }
        // If defaultResult was false, then check if this BlockEntity belongs to a ship
        final BlockPos blockEntityPos = methodBlockEntity.getBlockPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos((ClientLevel) level, blockEntityPos);
        if (shipObject != null) {
            // Transform tileEntityPos to be in world coordinates
            final ShipTransform renderTransform = shipObject.getRenderTransform();
            final Vector3dc tileEntityPosInWorldCoordinates = renderTransform.getShipToWorldMatrix()
                .transformPosition(new Vector3d(tileEntityPos.x(), tileEntityPos.y(), tileEntityPos.z()));
            final Vec3 tileEntityPosInWorldCoordinatesVec3d =
                VectorConversionsMCKt.toMinecraft(tileEntityPosInWorldCoordinates);
            return tileEntityPosInWorldCoordinatesVec3d.closerThan(this.camera.getPosition(),
                instance.getViewDistance());
        }
        return false;
    }
}
