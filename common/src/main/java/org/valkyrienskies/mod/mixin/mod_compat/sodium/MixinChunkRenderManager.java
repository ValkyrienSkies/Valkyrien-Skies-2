package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.WeakHashMap;
import kotlin.Unit;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderColumn;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ChunkRenderManager.class, remap = false)
public abstract class MixinChunkRenderManager<T extends ChunkGraphicsState> {

    private final WeakHashMap<ShipObjectClient, ChunkRenderList<T>[]> shipRenderLists = new WeakHashMap<>();
    @Shadow
    @Final
    private ObjectList<ChunkRenderContainer<T>> tickableChunks;
    @Shadow
    private int visibleChunkCount;
    @Shadow
    @Final
    private ChunkRenderList<T>[] chunkRenderLists;
    @Shadow
    @Final
    private ChunkRenderBackend<T> backend;

    @Shadow
    protected abstract int computeVisibleFaces(ChunkRenderContainer<T> render);

    @Shadow
    protected abstract void addChunkToRenderLists(ChunkRenderContainer<T> render);

    @Shadow
    protected abstract ChunkRenderColumn<T> getColumn(int x, int z);

    @Shadow
    protected abstract void addChunk(ChunkRenderContainer<T> render);

    @Shadow
    protected abstract void addEntitiesToRenderLists(ChunkRenderContainer<T> render);

    @Inject(
        at = @At("TAIL"),
        method = "iterateChunks"
    )
    private void afterIterateChunks(final Camera camera, final FrustumExtended frustum, final int frame,
        final boolean spectator,
        final CallbackInfo ci) {

        for (final ShipObjectClient ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
            ship.getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                final ChunkRenderColumn<T> column = this.getColumn(x, z);
                if (column != null) {
                    for (int y = 0; y < 15; y++) {
                        if (column.getRender(y) != null) {
                            this.addChunk(column.getRender(y));
                        }
                    }
                }
                return Unit.INSTANCE;
            });
        }

    }

    @Inject(
        at = @At("HEAD"),
        method = "renderLayer",
        cancellable = true
    )
    private void beforeRenderLayer(final PoseStack matrixStack, final BlockRenderPass pass, final double camX,
        final double camY, final double camZ, final CallbackInfo ci) {

        ci.cancel();
        final RenderDevice device = RenderDevice.INSTANCE;
        final CommandList commandList = device.createCommandList();

        final ChunkRenderList<T> chunkRenderList = this.chunkRenderLists[pass.ordinal()];
        final ChunkRenderListIterator<T> iterator = chunkRenderList.iterator(pass.isTranslucent());
        this.backend.begin(matrixStack);
        this.backend.render(commandList, iterator, new ChunkCameraContext(camX, camY, camZ));
        this.backend.end(matrixStack);

        this.shipRenderLists.forEach((ship, shipRenderLists) -> {
            final ChunkRenderList<T> shipRenderList = shipRenderLists[pass.ordinal()];
            final ChunkRenderListIterator<T> shipRenderListIter = shipRenderList.iterator(pass.isTranslucent());

            final Vector3dc center = ship.getRenderTransform().getShipPositionInShipCoordinates();
            matrixStack.pushPose();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), matrixStack, center.x(), center.y(),
                center.z(), camX, camY, camZ);

            final Vector3d camInShip =
                ship.getRenderTransform().getWorldToShipMatrix().transformPosition(new Vector3d(camX, camY, camZ));

            this.backend.begin(matrixStack);
            this.backend.render(commandList, shipRenderListIter,
                new ChunkCameraContext(center.x(), center.y(), center.z()));
            this.backend.end(matrixStack);

            matrixStack.popPose();
        });

        commandList.flush();
    }

    @Inject(
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager;useFogCulling:Z"
        ),
        method = "addChunk",
        cancellable = true
    )
    private void beforeAddChunk(final ChunkRenderContainer<T> render, final CallbackInfo ci) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(
            Minecraft.getInstance().level, render.getChunkX(), render.getChunkZ());

        if (ChunkAllocator.isChunkInShipyard(render.getChunkX(), render.getChunkZ()) && ship == null) {
            System.err.println("WARNING: SODIUM LOADED CHUNK IN SHIPYARD, BUT NO SHIP ATTACHED");
            return;
        }

        if (ship != null) {
            ci.cancel();
            if (!render.isEmpty()) {
                final ChunkRenderList<T>[] shipRenderList =
                    shipRenderLists.computeIfAbsent(ship, k -> createShipRenderLists());

                addChunkToSpecificRenderList(render, shipRenderList);
                addEntitiesToRenderLists(render);
            }
        }

    }

    private void addChunkToSpecificRenderList(final ChunkRenderContainer<T> render,
        final ChunkRenderList<T>[] renderList) {
        // todo compute visible faces correctly
        final int visibleFaces = this.computeVisibleFaces(render) & render.getFacesWithData();
        if (visibleFaces != 0) {
            boolean added = false;
            final T[] states = render.getGraphicsStates();

            for (int i = 0; i < states.length; ++i) {
                final T state = states[i];
                if (state != null) {
                    final ChunkRenderList<T> list = renderList[i];
                    list.add(state, visibleFaces);
                    added = true;
                }
            }

            if (added) {
                if (render.isTickable()) {
                    this.tickableChunks.add(render);
                }

                ++this.visibleChunkCount;
            }

        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private ChunkRenderList<T>[] createShipRenderLists() {
        final ChunkRenderList<T>[] renderLists = new ChunkRenderList[BlockRenderPass.COUNT];

        for (int i = 0; i < renderLists.length; ++i) {
            renderLists[i] = new ChunkRenderList<>();
        }

        return renderLists;
    }

}
