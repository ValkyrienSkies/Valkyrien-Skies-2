package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Matrix4dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.sodium.ShipSectionCache;
import org.valkyrienskies.mod.compat.sodium.ShipSectionCandidate;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

/**
 * Hi! Not many people read Valkyrien Skies' code, and even fewer will read this particular file. If you're
 * here because you're contributing to VS, thank you so much! This is complex stuff, and your work is appreciated by
 * all of our users.
 * <p>
 * If you're here because you develop a competitor mod, we can't stop you from using this code - but at least have
 * the decency to give credit to us, the original authors, and abide by the terms of our open source license. Don't
 * pretend that you wrote this code. That's not cool.
 *
 * @author Rubydesic
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager implements RenderSectionManagerDuck {

    @Unique
    private final WeakHashMap<ClientShip, SortedRenderLists> shipRenderLists = new WeakHashMap<>();

    @Unique
    private final WeakHashMap<ClientShip, ShipSectionCache> vs$shipSectionCaches = new WeakHashMap<>();

    @Unique
    private boolean vs$shipRenderListsDirty = true;

    @Unique
    private int vs$shipSectionCacheGeneration = 1;

    @Unique
    private int vs$lastShipRenderListFrame = Integer.MIN_VALUE;

    @Override
    public WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists() {
        return shipRenderLists;
    }

    @Override
    public void vs$markShipRenderListsDirty() {
        this.vs$shipRenderListsDirty = true;
        this.vs$shipSectionCacheGeneration++;
    }

    @Override
    public void vs$invalidateShipSectionCache(final ClientShip ship) {
        this.vs$shipSectionCaches.remove(ship);
    }

    @Shadow
    @Final
    private ClientLevel world;

    @Shadow
    private SortedRenderLists renderLists;

    @Shadow
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow
    private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;

    @Shadow
    public abstract void tickVisibleRenders();

    @Inject(at = @At("TAIL"), method = "createTerrainRenderList")
    private void afterIterateChunks(final Camera camera, final Viewport viewport, final int frame,
        final boolean spectator, final CallbackInfo ci) {
        this.vs$updateShipRenderLists(camera, viewport, frame, spectator);
    }

    @Override
    public void vs$updateShipRenderLists(final Camera camera, final Viewport viewport, final int frame,
        final boolean spectator) {
        if (this.vs$lastShipRenderListFrame == frame) {
            return;
        }
        this.vs$lastShipRenderListFrame = frame;

        final Minecraft minecraft = Minecraft.getInstance();
        final ProfilerFiller profiler = minecraft.getProfiler();
        profiler.push("vs_ship_render_lists");
        try {
            final Iterable<ClientShip> loadedShips = VSGameUtilsKt.getShipObjectWorld(minecraft).getLoadedShips();
            shipRenderLists.clear();
            boolean mergedRebuilds = false;

            for (final ClientShip ship : loadedShips) {
                final AABBdc shipAabb = ship.getRenderAABB();
                if (shipAabb == null || !vs$isAabbVisible(viewport, shipAabb)) {
                    continue;
                }

                final VisibleChunkCollector collector = new VisibleChunkCollector(frame);
                final Matrix4dc shipToWorld = ship.getRenderTransform().getShipToWorld();
                final AABBd tempAabb = new AABBd();
                int visibleSectionCount = 0;

                for (final ShipSectionCandidate candidate : this.vs$getShipSectionCache(ship, frame).sections) {
                    if (!vs$isShipSectionVisible(viewport, shipToWorld, tempAabb, candidate.x, candidate.y, candidate.z)) {
                        continue;
                    }

                    final RenderSection section = candidate.section;

                    if (section == null) {
                        continue;
                    }

                    collector.visit(section, true);
                    visibleSectionCount++;
                }

                if (visibleSectionCount == 0) {
                    continue;
                }

                shipRenderLists.put(ship, collector.createRenderLists());

                // merge rebuild lists
                for (final var entry : collector.getRebuildLists().entrySet()) {
                    this.rebuildLists.get(entry.getKey()).addAll(entry.getValue());
                    mergedRebuilds = true;
                }
            }
            if (mergedRebuilds) {
                this.rebuildLists.forEach(
                    (type, rebuildLists) -> {
                        final List<RenderSection> rebuildSorted = new ArrayList<>(rebuildLists);
                        rebuildSorted.sort(Comparator.comparingDouble(section -> section.getSquaredDistance(camera.getBlockPosition())));
                        rebuildLists.clear();
                        rebuildLists.addAll(rebuildSorted);
                    }
                );
            }

            this.vs$shipRenderListsDirty = false;
        } finally {
            profiler.pop();
        }
    }

    @Unique
    private ShipSectionCache vs$getShipSectionCache(final ClientShip ship, final int frame) {
        final ShipSectionCache cached = this.vs$shipSectionCaches.get(ship);
        final int activeChunkCount = ship.getActiveChunksSet().getSize();
        if (
            cached != null
                && !this.vs$shipRenderListsDirty
                && cached.activeChunkCount == activeChunkCount
                && cached.dirtyGeneration == this.vs$shipSectionCacheGeneration
        ) {
            return cached;
        }

        final ArrayList<ShipSectionCandidate> sections = new ArrayList<>();
        ship.getActiveChunksSet().forEach((x, z) -> {
            final LevelChunk levelChunk = world.getChunk(x, z);
            for (int y = world.getMinSection(); y < world.getMaxSection(); y++) {
                final LevelChunkSection levelChunkSection = levelChunk.getSection(y - world.getMinSection());
                if (levelChunkSection.hasOnlyAir()) {
                    continue;
                }

                final RenderSection renderSection = getRenderSection(x, y, z);
                if (renderSection == null) {
                    continue;
                }

                sections.add(new ShipSectionCandidate(x, y, z, renderSection));
            }
        });

        final ShipSectionCache rebuilt = new ShipSectionCache(sections, activeChunkCount, this.vs$shipSectionCacheGeneration);
        this.vs$shipSectionCaches.put(ship, rebuilt);
        return rebuilt;
    }

    @Unique
    private static boolean vs$isShipSectionVisible(final Viewport viewport, final Matrix4dc shipToWorld,
        final AABBd tempAabb, final int x, final int y, final int z) {
        tempAabb.setMin((x << 4) - 0.6, (y << 4) - 0.6, (z << 4) - 0.6);
        tempAabb.setMax((x << 4) + 15.6, (y << 4) + 15.6, (z << 4) + 15.6);
        tempAabb.transform(shipToWorld);
        return vs$isAabbVisible(viewport, tempAabb);
    }

    @Unique
    private static boolean vs$isAabbVisible(final Viewport viewport, final AABBdc aabb) {
        final double centerX = (aabb.minX() + aabb.maxX()) * 0.5;
        final double centerY = (aabb.minY() + aabb.maxY()) * 0.5;
        final double centerZ = (aabb.minZ() + aabb.maxZ()) * 0.5;
        final int x = Mth.floor(centerX);
        final int y = Mth.floor(centerY);
        final int z = Mth.floor(centerZ);
        final float extentX = (float) ((aabb.maxX() - aabb.minX()) * 0.5 + Math.abs(centerX - x) + 1.0);
        final float extentY = (float) ((aabb.maxY() - aabb.minY()) * 0.5 + Math.abs(centerY - y) + 1.0);
        final float extentZ = (float) ((aabb.maxZ() - aabb.minZ()) * 0.5 + Math.abs(centerZ - z) + 1.0);
        return viewport.isBoxVisible(x, y, z, extentX, extentY, extentZ);
    }

    @WrapMethod(method = "tickVisibleRenders")
    private void tickVisibleShipRenders(Operation<Void> original) {
        original.call();

        SortedRenderLists trueRenderLists = renderLists;

        for (final SortedRenderLists currentShipRenderLists : shipRenderLists.values()) {
            renderLists = currentShipRenderLists;
            original.call();
        }

        renderLists = trueRenderLists;
    }

    @Inject(at = @At("TAIL"), method = "resetRenderLists")
    private void afterResetLists(final CallbackInfo ci) {
        shipRenderLists.clear();
        vs$shipSectionCaches.clear();
        vs$shipRenderListsDirty = true;
    }
}
