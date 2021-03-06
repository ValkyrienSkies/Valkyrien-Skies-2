package org.valkyrienskies.mod.mixin.client.render.block.entity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
    public World world;

    /**
     * This mixin fixes the culling of {@link BlockEntity}s that belong to a ship.
     */
    @Redirect(
        method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/Vec3d;isInRange(Lnet/minecraft/util/math/Position;D)Z"
        )
    )
    private boolean isTileEntityInRenderRange(final Vec3d tileEntityPos, final Position cameraPos,
        final double radiusSquared, final BlockEntity methodBlockEntity, final float methodTickDelta,
        final MatrixStack methodMatrix, final VertexConsumerProvider methodVertexConsumerProvider) {

        final boolean defaultResult = tileEntityPos.isInRange(cameraPos, radiusSquared);
        if (defaultResult) {
            return true;
        }
        // If defaultResult was false, then check if this BlockEntity belongs to a ship
        final BlockPos blockEntityPos = methodBlockEntity.getPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos((ClientWorld) world, blockEntityPos);
        if (shipObject != null) {
            // Transform tileEntityPos to be in world coordinates
            final ShipTransform renderTransform = shipObject.getRenderTransform();
            final Vector3dc tileEntityPosInWorldCoordinates = renderTransform.getShipToWorldMatrix()
                .transformPosition(new Vector3d(tileEntityPos.getX(), tileEntityPos.getY(), tileEntityPos.getZ()));
            final Vec3d tileEntityPosInWorldCoordinatesVec3d =
                VectorConversionsMCKt.toVec3d(tileEntityPosInWorldCoordinates);
            return tileEntityPosInWorldCoordinatesVec3d.isInRange(cameraPos, radiusSquared);
        }
        return false;
    }
}
