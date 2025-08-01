package org.valkyrienskies.mod.mixin.mod_compat.embeddium;

import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.lists.VisibleChunkCollector;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.embeddium.RenderSectionManagerDuck;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

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

    @Override
    public WeakHashMap<ClientShip, SortedRenderLists> vs_getShipRenderLists() {
        return shipRenderLists;
    }

    @Shadow
    @Final
    private ClientLevel world;

    @Shadow
    protected abstract RenderSection getRenderSection(int x, int y, int z);

    @Shadow
    private Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists;

    @Inject(at = @At("TAIL"), method = "createTerrainRenderList")
    private void afterIterateChunks(Camera camera, Viewport viewport, int frame, boolean spectator, CallbackInfo ci) {
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
            final VisibleChunkCollector collector = new VisibleChunkCollector(frame);

            ship.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = world.getChunk(x, z);
                for (int y = world.getMinSection(); y < world.getMaxSection(); y++) {
                    // If the chunk section is empty then skip it
                    final LevelChunkSection levelChunkSection = levelChunk.getSection(y - world.getMinSection());
                    if (levelChunkSection.hasOnlyAir()) {
                        continue;
                    }
                    // TODO: Add occlusion logic here?

                    final RenderSection section = getRenderSection(x, y, z);

                    if (section == null) {
                        continue;
                    }

                    collector.visit(section,true);
                }
            });

            shipRenderLists.put(ship, collector.createRenderLists());

            // merge rebuild lists
            for (final var entry : collector.getRebuildLists().entrySet()) {
                this.rebuildLists.get(entry.getKey()).addAll(entry.getValue());
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "resetRenderLists")
    private void afterResetLists(final CallbackInfo ci) {
        shipRenderLists.clear();
    }
}
