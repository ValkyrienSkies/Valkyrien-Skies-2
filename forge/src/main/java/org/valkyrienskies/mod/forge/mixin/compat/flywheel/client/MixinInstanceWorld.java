package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import com.jozufozu.flywheel.backend.instancing.InstanceWorld;
import com.jozufozu.flywheel.backend.material.MaterialManager;
import com.jozufozu.flywheel.core.shader.WorldProgram;
import com.jozufozu.flywheel.event.RenderLayerEvent;
import com.mojang.math.Matrix4f;
import java.util.WeakHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.forge.mixinducks.MixinTileInstanceManagerDuck;

@Mixin(InstanceWorld.class)
public class MixinInstanceWorld {

    @Shadow
    @Final
    protected InstanceManager<BlockEntity> tileEntityInstanceManager;

    @Inject(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;clearRenderState()V"
        )
    )
    void renderShipTiles(final RenderLayerEvent event, final CallbackInfo ci) {
        final WeakHashMap<ShipObjectClient, MaterialManager<WorldProgram>> shipManagers =
            ((MixinTileInstanceManagerDuck) tileEntityInstanceManager).getShipMaterialManagers();

        shipManagers.forEach((ship, manager) -> {
            final Matrix4f viewProjection = new Matrix4f(event.viewProjection);
            transformRenderWithShip(ship.getRenderTransform(), viewProjection, event.camX, event.camY, event.camZ);
            manager.render(event.layer, viewProjection, 0, 0, 0);
        });
    }

    @Unique
    private static void transformRenderWithShip(final ShipTransform renderTransform, final Matrix4f matrix,
        final double camX, final double camY, final double camZ) {

        final Matrix4dc shipToWorldMatrix = renderTransform.getShipToWorldMatrix();

        // Create the render matrix from the render transform and player position
        final Matrix4d renderMatrix = new Matrix4d();
        renderMatrix.translate(-camX, -camY, -camZ);
        renderMatrix.mul(shipToWorldMatrix);

        // Apply the render matrix to the
        matrix.multiply(VectorConversionsMCKt.toMinecraft(renderMatrix));
    }
}
