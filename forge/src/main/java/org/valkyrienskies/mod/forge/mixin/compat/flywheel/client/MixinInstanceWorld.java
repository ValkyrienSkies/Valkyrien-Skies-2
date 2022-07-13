package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import com.jozufozu.flywheel.backend.instancing.InstanceWorld;
import com.jozufozu.flywheel.backend.material.MaterialManager;
import com.jozufozu.flywheel.core.shader.WorldProgram;
import com.jozufozu.flywheel.event.RenderLayerEvent;
import java.util.WeakHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.forge.mixinducks.MixinTileInstanceManagerDuck;

@Mixin(InstanceWorld.class)
public class MixinInstanceWorld {

    @Shadow
    @Final
    protected InstanceManager<BlockEntity> tileEntityInstanceManager;

//    @Inject(
//        method = "beginFrame",
//        at = @At("TAIL")
//    )
//    void beginFrame(final BeginFrameEvent event, final CallbackInfo ci) {
//        final WeakHashMap<ShipObjectClient, MaterialManager<WorldProgram>> shipManagers =
//            ((MixinTileInstanceManagerDuck) tileEntityInstanceManager).getShipMaterialManagers();
//
//        shipManagers.forEach((ship, manager) -> {
//
//        });
//    }

    @Inject(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/backend/material/MaterialManager;render(Lcom/jozufozu/flywheel/backend/state/RenderLayer;Lcom/mojang/math/Matrix4f;DDD)V"
        )
    )
    void renderShipTiles(final RenderLayerEvent event, final CallbackInfo ci) {
        final WeakHashMap<ShipObjectClient, MaterialManager<WorldProgram>> shipManagers =
            ((MixinTileInstanceManagerDuck) tileEntityInstanceManager).getShipMaterialManagers();

        shipManagers.forEach((ship, manager) -> {
            final Matrix4d viewProjection = VectorConversionsMCKt.toJOML(event.viewProjection);
            final Matrix4dc shipToWorldMatrix = ship.getRenderTransform().getShipToWorldMatrix();

//            final Matrix4d finalProjection = shipToWorldMatrix.mul(viewProjection, new Matrix4d());
            final Matrix4d finalProjection = viewProjection.mul(shipToWorldMatrix);

            manager.render(event.layer, VectorConversionsMCKt.toMinecraft(finalProjection), event.camX, event.camY,
                event.camZ);
        });
    }
}
