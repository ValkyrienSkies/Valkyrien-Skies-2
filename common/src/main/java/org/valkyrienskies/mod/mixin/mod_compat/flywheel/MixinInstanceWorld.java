// package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

// import com.jozufozu.flywheel.api.MaterialManager;
// import com.jozufozu.flywheel.backend.gl.GlStateTracker;
// import com.jozufozu.flywheel.backend.instancing.InstanceManager;
// import com.jozufozu.flywheel.backend.instancing.InstanceWorld;
// import com.jozufozu.flywheel.backend.instancing.ParallelTaskEngine;
// import com.jozufozu.flywheel.backend.instancing.batching.BatchingEngine;
// import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
// import com.jozufozu.flywheel.event.RenderLayerEvent;
// import net.minecraft.world.level.block.entity.BlockEntity;
// import org.joml.Matrix4d;
// import org.joml.Matrix4f;
// import org.joml.Vector3d;
// import org.spongepowered.asm.mixin.Final;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Pseudo;
// import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.Unique;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// import org.valkyrienskies.core.api.ships.ClientShip;
// import org.valkyrienskies.mod.common.VSClientGameUtils;
// import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
// import org.valkyrienskies.mod.compat.FlywheelEvents;
// import org.valkyrienskies.mod.mixinducks.MixinBlockEntityInstanceManagerDuck;
// import org.valkyrienskies.mod.mixinducks.MixinInstancingEngineDuck;

// @Pseudo
// @Mixin(value = InstanceWorld.class, remap = false)
// public class MixinInstanceWorld {

//     @Shadow
//     @Final
//     protected InstanceManager<BlockEntity> blockEntityInstanceManager;

//     @Shadow
//     @Final
//     public ParallelTaskEngine taskEngine;

//     @Inject(
//         method = "renderLayer",
//         at = @At(
//             value = "INVOKE",
//             target = "Lcom/jozufozu/flywheel/backend/instancing/Engine;render(Lcom/jozufozu/flywheel/backend/instancing/TaskEngine;Lcom/jozufozu/flywheel/event/RenderLayerEvent;)V"
//         )
//     )
//     void renderShipTiles(final RenderLayerEvent event, final CallbackInfo ci) {
//         //not sure if restoreState stuff should be here or in the ((MixinInstancingEngineDuck) manager).render() method
//         final GlStateTracker.State restoreState = GlStateTracker.getRestoreState();
//         final var shipManagers =
//             ((MixinBlockEntityInstanceManagerDuck) blockEntityInstanceManager).vs$getShipMaterialManagers();

//         shipManagers.forEach((ship, manager) -> vs$render(ship, manager, event));
//         restoreState.restore();
//     }

//     @Unique
//     private void vs$render(final ClientShip ship, final MaterialManager manager, final RenderLayerEvent event) {
//         if (manager instanceof final InstancingEngine<?> engine) {
//             final Vector3d origin = VectorConversionsMCKt.toJOMLD(engine.getOriginCoordinate());

//             final Matrix4d viewProjection = new Matrix4d(event.viewProjection);

//             final Matrix4d finalProjection = new Matrix4d()
//                 .mul(viewProjection)
//                 .translate(-event.camX, -event.camY, -event.camZ)
//                 .mul(ship.getRenderTransform().getShipToWorld())
//                 .translate(origin);

//             final Vector3d camInShipLocal = ship.getRenderTransform().getWorldToShip()
//                 .transformPosition(event.camX, event.camY, event.camZ, new Vector3d())
//                 .sub(origin);

//             final Matrix4f fnlProj = new Matrix4f(finalProjection);

//             ((MixinInstancingEngineDuck) engine).vs$render(
//                 fnlProj,
//                 camInShipLocal.x,
//                 camInShipLocal.y,
//                 camInShipLocal.z,
//                 event.layer
//             );
//         } else if (manager instanceof final BatchingEngine engine) {
//             event.stack.pushPose();
//             VSClientGameUtils.multiplyWithShipToWorld(event.stack, ship);
//             engine.render(taskEngine, event);
//             event.stack.popPose();
//         } else {
//             throw new IllegalArgumentException("unrecognized engine");
//         }
//     }

//     @Inject(method = "<init>", at = @At("RETURN"))
//     private void postInit(final CallbackInfo ci) {
//         FlywheelEvents.onInstanceWorldLoad(InstanceWorld.class.cast(this));
//     }

//     @Inject(method = "delete", at = @At("RETURN"))
//     private void postDelete(final CallbackInfo ci) {
//         FlywheelEvents.onInstanceWorldUnload(InstanceWorld.class.cast(this));
//     }
// }
