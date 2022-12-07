package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Map;
import java.util.WeakHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RegionChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderBounds;
import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager implements RenderSectionManagerDuck {

    @Unique
    private final WeakHashMap<ClientShip, ChunkRenderList> shipRenderLists = new WeakHashMap<>();

    @Override
    public WeakHashMap<ClientShip, ChunkRenderList> getShipRenderLists() {
        return shipRenderLists;
    }

    @Shadow
    @Final
    private ClientLevel world;
    @Shadow
    @Final
    private ObjectList<RenderSection> tickableChunks;
    @Shadow
    @Final
    private RegionChunkRenderer chunkRenderer;
    @Shadow
    @Final
    private Map<ChunkUpdateType, PriorityQueue<RenderSection>> rebuildQueues;
    @Shadow
    private float cameraX;
    @Shadow
    private float cameraY;
    @Shadow
    private float cameraZ;

    @Shadow
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow
    protected abstract void addEntitiesToRenderLists(RenderSection render);

    @Shadow
    @Final
    private static double NEARBY_CHUNK_DISTANCE;

    @Inject(at = @At("TAIL"), method = "iterateChunks")
    private void afterIterateChunks(final Camera camera, final Frustum frustum, final int frame,
        final boolean spectator, final CallbackInfo ci) {
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
            ship.getActiveChunksSet().forEach((x, z) -> {
                for (int y = world.getMinSection(); y < world.getMaxSection(); y++) {
                    final RenderSection section = getRenderSection(x, y, z);

                    if (section == null) {
                        continue;
                    }

                    if (section.getPendingUpdate() != null) {
                        final PriorityQueue<RenderSection> queue = this.rebuildQueues.get(section.getPendingUpdate());
                        if (queue.size() < (2 << 4) - 1) {
                            queue.enqueue(section);
                        }
                    }

                    final ChunkRenderBounds b = section.getBounds();
                    final AABBd b2 = new AABBd(b.x1 - 6e-1, b.y1 - 6e-1, b.z1 - 6e-1,
                        b.x2 + 6e-1, b.y2 + 6e-1, b.z2 + 6e-1)
                        .transform(ship.getRenderTransform().getShipToWorld());

                    if (section.isEmpty() ||
                        !frustum.isBoxVisible((float) b2.minX, (float) b2.minY, (float) b2.minZ,
                            (float) b2.maxX, (float) b2.maxY, (float) b2.maxZ)) {
                        continue;
                    }

                    shipRenderLists.computeIfAbsent(ship, k -> new ChunkRenderList()).add(section);

                    if (section.isTickable()) {
                        tickableChunks.add(section);
                    }

                    addEntitiesToRenderLists(section);
                }
            });
        }
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;isChunkPrioritized(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;)Z"
        ),
        method = "scheduleRebuild"
    )
    private boolean redirectIsChunkPrioritized(final RenderSectionManager instance, final RenderSection render) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(world,
            render.getOriginX() + 8, render.getOriginY() + 8, render.getOriginZ() + 8,
            this.cameraX, this.cameraY, this.cameraZ) <= NEARBY_CHUNK_DISTANCE;
    }

    @Inject(at = @At("TAIL"), method = "resetLists")
    private void afterResetLists(final CallbackInfo ci) {
        shipRenderLists.values().forEach(ChunkRenderList::clear);
    }
}
