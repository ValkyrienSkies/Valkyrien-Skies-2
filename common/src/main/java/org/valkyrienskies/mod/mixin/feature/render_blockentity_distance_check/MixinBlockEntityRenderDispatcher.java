package org.valkyrienskies.mod.mixin.feature.render_blockentity_distance_check;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
        final Vec3 cameraPos) {

        final boolean defaultResult = instance.shouldRender(methodBlockEntity, cameraPos);
        if (defaultResult) {
            return true;
        }

        // If defaultResult was false, then check if this BlockEntity belongs to a ship
        final BlockPos bePos = methodBlockEntity.getBlockPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos((ClientLevel) level, bePos);
        if (shipObject != null) {
            final Matrix4dc m = shipObject.getRenderTransform().getShipToWorldMatrix();

            return new Vec3(
                m.m00() * bePos.getX() + m.m10() * bePos.getY() + m.m20() * bePos.getZ() + m.m30(),
                m.m01() * bePos.getX() + m.m11() * bePos.getY() + m.m21() * bePos.getZ() + m.m31(),
                m.m02() * bePos.getX() + m.m12() * bePos.getY() + m.m22() * bePos.getZ() + m.m32()
            ).closerThan(this.camera.getPosition(), instance.getViewDistance());
        }
        return false;
    }
}
