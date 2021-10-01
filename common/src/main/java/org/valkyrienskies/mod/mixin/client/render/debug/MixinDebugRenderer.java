package org.valkyrienskies.mod.mixin.client.render.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
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
    private void postRender(final MatrixStack matrices, final VertexConsumerProvider.Immediate vertexConsumers,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        final ClientWorld world = MinecraftClient.getInstance().world;
        final ShipObjectClientWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        for (final ShipObjectClient shipObjectClient : shipObjectClientWorld.getShipObjects().values()) {
            final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
            final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();
            final Quaterniondc shipRenderOrientation =
                shipRenderTransform.getShipCoordinatesToWorldCoordinatesRotation();

            final double renderRadius = .25;
            final Box shipCenterOfMassBox =
                new Box(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                    shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                    shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius)
                    .offset(-cameraX, -cameraY, -cameraZ);

            WorldRenderer
                .drawBox(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), shipCenterOfMassBox,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
