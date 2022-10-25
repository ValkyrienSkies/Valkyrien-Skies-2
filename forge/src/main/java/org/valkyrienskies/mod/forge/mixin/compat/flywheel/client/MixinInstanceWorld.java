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
import org.joml.Vector3d;
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
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(manager.getOriginCoordinate());

            final Matrix4d viewProjection = VectorConversionsMCKt.toJOML(event.viewProjection);
            final Matrix4dc shipToWorldMatrix = ship.getRenderTransform().getShipToWorldMatrix();

            final Matrix4d finalProjection = new Matrix4d()
                .mul(viewProjection)
                .translate(-event.camX, -event.camY, -event.camZ)
                .mul(shipToWorldMatrix)
                .translate(origin);

            final Vector3d camInShipLocal = ship.getRenderTransform().getWorldToShipMatrix()
                .transformPosition(event.camX, event.camY, event.camZ, new Vector3d())
                .sub(origin);

            manager.render(event.layer, VectorConversionsMCKt.toMinecraft(finalProjection), camInShipLocal.x,
                camInShipLocal.y,
                camInShipLocal.z);
        });
    }
}
