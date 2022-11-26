//package org.valkyrienskies.mod.mixin.mod_compat.sodium;
//
//import it.unimi.dsi.fastutil.objects.ObjectList;
//import java.util.WeakHashMap;
//import kotlin.Unit;
//import me.jellysquid.mods.sodium.client.gl.device.CommandList;
//import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
//import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
//import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
//import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderList;
//import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
//import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
//import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
//import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkFaceFlags;
//import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
//import me.jellysquid.mods.sodium.client.render.chunk.graph.ChunkGraphIterationQueue;
//import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
//import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
//import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
//import net.minecraft.client.Camera;
//import net.minecraft.client.Minecraft;
//import net.minecraft.core.Direction;
//import org.joml.Vector3d;
//import org.joml.Vector3dc;
//import org.spongepowered.asm.mixin.Final;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.Redirect;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.valkyrienskies.core.game.ChunkAllocator;
//import org.valkyrienskies.core.api.ships.ClientShip;
//import org.valkyrienskies.mod.common.VSClientGameUtils;
//import org.valkyrienskies.mod.common.VSGameUtilsKt;
//import org.valkyrienskies.mod.compat.IrisCompat;
//
//@Mixin(value = RenderSectionManager.class, remap = false)
//public abstract class MixinChunkRenderManager<T extends ChunkGraphicsState> {
//
//    private final WeakHashMap<ShipObjectClient, ChunkRenderList[]> shipRenderLists = new WeakHashMap<>();
//    @Shadow
//    @Final
//    private ObjectList<RenderSection> tickableChunks;
//    @Shadow
//    private int visibleChunkCount;
//    @Shadow
//    @Final
//    private ChunkRenderList<T>[] chunkRenderLists;
//    @Shadow
//    @Final
//    private RenderSection backend;
//
//    @Shadow
//    protected abstract RenderSection getRenderSection(int x, int y, int z);
//
//    @Shadow
//    protected abstract void addChunkToVisible(RenderSection render);
//
//    @Shadow
//    protected abstract void addEntitiesToRenderLists(RenderSection render);
//
//    @Shadow
//    @Final
//    private boolean useBlockFaceCulling;
//
//    @Shadow
//    private float cameraX;
//
//    @Shadow
//    private float cameraY;
//
//    @Shadow
//    private float cameraZ;
//
//    @Shadow
//    @Final
//    private ChunkGraphIterationQueue iterationQueue;
//
//    @Shadow
//    protected abstract void addVisible(RenderSection render, Direction flow);
//
//    @Redirect(
//        at = @At(
//            value = "INVOKE",
//            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;addVisible(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;Lnet/minecraft/core/Direction;)V"
//        ),
//        method = "bfsEnqueue"
//    )
//    private void redirectIterateChunks(final RenderSectionManager instance, final RenderSection render,
//        final Direction flow) {
//        if (ChunkAllocator.isChunkInShipyard(render.getChunkX(), render.getChunkZ())) {
//            return;
//        }
//
//        addVisible(render, flow);
//    }
//
//    /**
//     * This code resets the ship's ChunkRenderList when normal ChunkRenderList gets reset
//     */
//    @Inject(
//        at = @At(
//            value = "TAIL"
//        ),
//        method = "resetLists"
//    )
//    private void injectReset(final CallbackInfo ci) {
//        shipRenderLists.forEach((ship, shipRenderLists) -> {
//            for (final ChunkRenderList list : shipRenderLists) {
//                list.clear();
//            }
//        });
//    }
//
//    /**
//     * Tells sodium to render chunks on the ship. I am not impressed with this code. We should try to figure out how to
//     * use sodium's chunk culler for the ships. Need to transform the camera/frustum into shipspace
//     */
//    @Inject(
//        at = @At("TAIL"),
//        method = "iterateChunks"
//    )
//    private void afterIterateChunks(final Camera camera, final Frustum frustum, final int frame,
//        final boolean spectator,
//        final CallbackInfo ci) {
//        final ChunkGraphIterationQueue queue = this.iterationQueue;
//        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
//            ship.getShipActiveChunksSet().iterateChunkPos((x, z) -> {
//                final RenderSection column = this.getRenderSection(x, 0, z);
//                if (column != null) {
//                    for (int y = 0; y < 15; y++) {
//                        if (queue.getRender(y) != null) {
//                            this.addChunkToVisible(queue.getRender(y));
//                        }
//                    }
//                }
//                return Unit.INSTANCE;
//            });
//        }
//
//    }
//
//    /**
//     * this mixin renders ship render lists
//     */
//    @Inject(
//        at = @At("HEAD"),
//        method = "renderLayer",
//        cancellable = true
//    )
//    private void beforeRenderLayer(final ChunkRenderMatrices matrixStack, final BlockRenderPass pass, final double camX,
//        final double camY, final double camZ, final CallbackInfo ci) {
//
//        ci.cancel();
//        final RenderDevice device = RenderDevice.INSTANCE;
//        final CommandList commandList = device.createCommandList();
//
//        final ChunkRenderList<T> chunkRenderList = this.chunkRenderLists[pass.ordinal()];
//        final ChunkRenderListIterator<T> iterator = chunkRenderList.iterator(pass.isTranslucent());
//        IrisCompat.tryIrisBegin(backend, matrixStack, pass);
//        this.backend.render(commandList, iterator, new ChunkCameraContext(camX, camY, camZ));
//        this.backend.end(matrixStack);
//
//        this.shipRenderLists.forEach((ship, shipRenderLists) -> {
//            final ChunkRenderList<T> shipRenderList = shipRenderLists[pass.ordinal()];
//            final ChunkRenderListIterator<T> shipRenderListIter = shipRenderList.iterator(pass.isTranslucent());
//
//            final Vector3dc center = ship.getRenderTransform().getShipPositionInShipCoordinates();
//            matrixStack.pushPose();
//
//            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), matrixStack, center.x(), center.y(),
//                center.z(), camX, camY, camZ);
//
//            IrisCompat.tryIrisBegin(backend, matrixStack, pass);
//            this.backend.render(commandList, shipRenderListIter,
//                new ChunkCameraContext(center.x(), center.y(), center.z()));
//            this.backend.end(matrixStack);
//
//            matrixStack.popPose();
//        });
//
//        commandList.flush();
//    }
//
//    /**
//     * This mixin adds ship chunks to a separate ChunkRenderList
//     */
//    @Inject(
//        at = @At(
//            value = "FIELD",
//            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;useFogCulling:Z"
//        ),
//        method = "addVisible",
//        cancellable = true
//    )
//    private void beforeAddChunk(final RenderSection render, final Direction flow, final CallbackInfo ci) {
//        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(
//            Minecraft.getInstance().level, render.getChunkX(), render.getChunkZ());
//
//        if (ChunkAllocator.isChunkInShipyard(render.getChunkX(), render.getChunkZ()) && ship == null) {
//            System.err.println("WARNING: SODIUM LOADED CHUNK IN SHIPYARD, BUT NO SHIP ATTACHED");
//            return;
//        }
//
//        if (ship != null) {
//            ci.cancel();
//            if (!render.isEmpty()) {
//                final ChunkRenderList[] shipRenderList =
//                    shipRenderLists.computeIfAbsent(ship, k -> createShipRenderLists());
//                final Vector3d camInShip = ship.getRenderTransform()
//                    .getWorldToShipMatrix().transformPosition(new Vector3d(cameraX, cameraY, cameraZ));
//
//                addChunkToSpecificRenderList(render, shipRenderList, camInShip);
//                addEntitiesToRenderLists(render);
//
//            }
//        }
//
//    }
//
//    @Unique
//    private int computeVisibleFaces(final RenderSection render, final Vector3dc cam) {
//        if (!this.useBlockFaceCulling) {
//            return ChunkFaceFlags.ALL;
//        } else {
//            final ChunkRenderBounds bounds = render.getBounds();
//            int visibleFaces = ChunkFaceFlags.UNASSIGNED;
//            if (cam.y() > bounds.y1) {
//                visibleFaces |= ChunkFaceFlags.UP;
//            }
//
//            if (cam.y() < bounds.y2) {
//                visibleFaces |= ChunkFaceFlags.DOWN;
//            }
//
//            if (cam.x() > bounds.x1) {
//                visibleFaces |= ChunkFaceFlags.EAST;
//            }
//
//            if (cam.x() < bounds.x2) {
//                visibleFaces |= ChunkFaceFlags.WEST;
//            }
//
//            if (cam.z() > bounds.z1) {
//                visibleFaces |= ChunkFaceFlags.SOUTH;
//            }
//
//            if (cam.z() < bounds.z2) {
//                visibleFaces |= ChunkFaceFlags.NORTH;
//            }
//
//            return visibleFaces;
//        }
//    }
//
//    private void addChunkToSpecificRenderList(final RenderSection render,
//        final ChunkRenderList[] renderList, final Vector3dc camInShip) {
//        final int visibleFaces = this.computeVisibleFaces(render, camInShip) & render.getFacesWithData();
//        if (visibleFaces != 0) {
//            boolean added = false;
//            final T[] states = render.getGraphicsStates();
//
//            for (int i = 0; i < states.length; ++i) {
//                final T state = states[i];
//                if (state != null) {
//                    final ChunkRenderList<T> list = renderList[i];
//
//                    list.add(state, visibleFaces);
//
//                    added = true;
//                }
//            }
//
//            if (added) {
//                if (render.isTickable()) {
//                    this.tickableChunks.add(render);
//                }
//
//                ++this.visibleChunkCount;
//            }
//
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    @Unique
//    private ChunkRenderList[] createShipRenderLists() {
//        final ChunkRenderList[] renderLists = new ChunkRenderList[BlockRenderPass.COUNT];
//
//        for (int i = 0; i < renderLists.length; ++i) {
//            renderLists[i] = new ChunkRenderList();
//        }
//
//        return renderLists;
//    }
//
//}
