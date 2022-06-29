package org.valkyrienskies.mod.mixin.client.render.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matrices, final MultiBufferSource.BufferSource vertexConsumers,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        final ClientLevel world = Minecraft.getInstance().level;
        final ShipObjectClientWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        if (Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            for (final ShipObjectClient shipObjectClient : shipObjectClientWorld.getShipObjects().values()) {
                final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
                final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();
                final Quaterniondc shipRenderOrientation =
                    shipRenderTransform.getShipCoordinatesToWorldCoordinatesRotation();

                final double renderRadius = .25;
                final AABB shipCenterOfMassBox =
                    new AABB(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                        shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                        shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius)
                        .move(-cameraX, -cameraY, -cameraZ);
                LevelRenderer
                    .renderLineBox(matrices, vertexConsumers.getBuffer(RenderType.lines()), shipCenterOfMassBox,
                        1.0F, 1.0F, 1.0F, 1.0F);

                final AABBdc shipAABBdc = shipObjectClient.getDebugShipPhysicsAABB();
                final AABB shipAABB =
                    new AABB(shipAABBdc.minX(), shipAABBdc.minY(), shipAABBdc.minZ(), shipAABBdc.maxX(),
                        shipAABBdc.maxY(), shipAABBdc.maxZ());
                LevelRenderer
                    .renderLineBox(matrices, vertexConsumers.getBuffer(RenderType.lines()),
                        shipAABB.move(-cameraX, -cameraY, -cameraZ),
                        1.0F, 0.0F, 0.0F, 1.0F);
            }
        }
    }
}
