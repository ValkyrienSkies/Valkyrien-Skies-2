package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.jozufozu.flywheel.backend.gl.GlStateTracker;
import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import com.jozufozu.flywheel.backend.instancing.InstanceWorld;
import com.jozufozu.flywheel.event.RenderLayerEvent;
import com.mojang.math.Matrix4f;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.MixinInstancingEngineDuck;
import org.valkyrienskies.mod.mixinducks.MixinTileInstanceManagerDuck;

@Pseudo
@Mixin(value = InstanceWorld.class, remap = false)
public class MixinInstanceWorld {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger("VS2 flywheel.client.MixinInstanceWorld");

    @Shadow
    @Final
    protected InstanceManager<BlockEntity> blockEntityInstanceManager;

    @Inject(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/backend/instancing/Engine;render(Lcom/jozufozu/flywheel/backend/instancing/TaskEngine;Lcom/jozufozu/flywheel/event/RenderLayerEvent;)V"
        )
    )
    void renderShipTiles(final RenderLayerEvent event, final CallbackInfo ci) {
        //not sure if restoreState stuff should be here or in the ((MixinInstancingEngineDuck) manager).render() method
        final GlStateTracker.State restoreState = GlStateTracker.getRestoreState();
        final var shipManagers = ((MixinTileInstanceManagerDuck) blockEntityInstanceManager).getShipInstancingEngines();

        shipManagers.forEach((ship, manager) -> {
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(manager.getOriginCoordinate());

            final Matrix4d viewProjection = VectorConversionsMCKt.toJOML(event.viewProjection);

            final Matrix4d finalProjection = new Matrix4d()
                .mul(viewProjection)
                .translate(-event.camX, -event.camY, -event.camZ)
                .mul(ship.getRenderTransform().getShipToWorld())
                .translate(origin);

            final Vector3d camInShipLocal = ship.getRenderTransform().getWorldToShip()
                .transformPosition(event.camX, event.camY, event.camZ, new Vector3d())
                .sub(origin);

            final Matrix4f fnlProj = VectorConversionsMCKt.toMinecraft(finalProjection);

            ((MixinInstancingEngineDuck) manager).render(
                fnlProj,
                camInShipLocal.x,
                camInShipLocal.y,
                camInShipLocal.z,
                event.layer
            );
        });
        restoreState.restore();

    }
}
