package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

/*
import dev.engine_room.flywheel.backend.gl.GlStateTracker;
import dev.engine_room.flywheel.impl.task.TaskExecutorImpl;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.FlywheelEvents;
import org.valkyrienskies.mod.mixinducks.MixinBlockEntityInstanceManagerDuck;
import org.valkyrienskies.mod.mixinducks.MixinInstancingEngineDuck;

@Pseudo
@Mixin(value = VisualizationManagerImpl.class, remap = false)
public class MixinInstanceWorld {

    // TODO: Probably make new VisualManagerImpl for each ship in the world and run them here
    // 	private final VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntities;
    //	private final VisualManagerImpl<Entity, EntityStorage> entities;
    //	private final VisualManagerImpl<Effect, EffectStorage> effects;
    @Shadow
    @Final
    private VisualManagerImpl<BlockEntity, BlockEntityStorage> blockEntities;

    @Shadow
    @Final
    private TaskExecutorImpl taskExecutor;

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
        final var shipManagers =
            ((MixinBlockEntityInstanceManagerDuck) blockEntities).vs$getShipMaterialManagers();

        shipManagers.forEach((ship, manager) -> vs$render(ship, manager, event));
        restoreState.restore();
    }

    @Unique
    private void vs$render(final ClientShip ship, final MaterialManager manager, final RenderLayerEvent event) {
        if (manager instanceof final InstancingEngine<?> engine) {
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(engine.getOriginCoordinate());

            final Matrix4d viewProjection = new Matrix4d(event.viewProjection);

            final Matrix4d finalProjection = new Matrix4d()
                .mul(viewProjection)
                .translate(-event.camX, -event.camY, -event.camZ)
                .mul(ship.getRenderTransform().getShipToWorld())
                .translate(origin);

            final Vector3d camInShipLocal = ship.getRenderTransform().getWorldToShip()
                .transformPosition(event.camX, event.camY, event.camZ, new Vector3d())
                .sub(origin);

            final Matrix4f fnlProj = new Matrix4f(finalProjection);

            ((MixinInstancingEngineDuck) engine).vs$render(
                fnlProj,
                camInShipLocal.x,
                camInShipLocal.y,
                camInShipLocal.z,
                event.layer
            );
        } else if (manager instanceof final BatchingEngine engine) {
            event.stack.pushPose();
            VSClientGameUtils.multiplyWithShipToWorld(event.stack, ship);
            engine.render(taskEngine, event);
            event.stack.popPose();
        } else {
            throw new IllegalArgumentException("unrecognized engine");
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postInit(final CallbackInfo ci) {
        FlywheelEvents.onInstanceWorldLoad(InstanceWorld.class.cast(this));
    }

    @Inject(method = "delete", at = @At("RETURN"))
    private void postDelete(final CallbackInfo ci) {
        FlywheelEvents.onInstanceWorldUnload(InstanceWorld.class.cast(this));
    }
}
 */
