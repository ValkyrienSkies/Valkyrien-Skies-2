package org.valkyrienskies.mod.mixin.client;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.mixinducks.client.render.VsViewArea;

@Mixin(ViewArea.class)
public abstract class MixinViewArea implements VsViewArea {

    @Shadow
    @javax.annotation.Nullable
    protected abstract RenderChunk getRenderChunkAt(BlockPos arg);

    @Unique
    private ChunkRenderDispatcher vs$chunkRenderDispatcher;

    @Unique
    private double vs$camX;
    @Unique
    private double vs$camZ;
    @Unique
    private boolean vs$didSetCam = false;

    @Unique
    @Nullable
    private ObjectArrayList<VsViewArea.ExtraChunk> vs$extraChunks = null;

    @Override
    @Nullable
    public Vector2d vs$getCameraPosition() {
        if (!vs$didSetCam)
            return null;

        return new Vector2d(vs$camX, vs$camZ);
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(
        final ChunkRenderDispatcher chunkRenderDispatcher,
        final Level level, final int renderDistance,
        final LevelRenderer levelRenderer,
        final CallbackInfo ci) {

        vs$chunkRenderDispatcher = chunkRenderDispatcher;
    }

    @Unique
    private int vs$findChunk(final int cx, final int cy, final int cz) {
        if (vs$extraChunks != null) {
            for (int i = 0; i < vs$extraChunks.size(); i++) {
                final var c = vs$extraChunks.get(i);
                if (c.cx() == cx && c.cy() == cy && c.cz() == cz) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Override
    public void vs$removeExtra(final int cx, final int cy, final int cz) {
        final int idx = vs$findChunk(cx, cy, cz);
        if (idx != -1) {
            assert vs$extraChunks != null;
            vs$extraChunks.remove(idx);
        }
    }

    @Unique
    private ChunkRenderDispatcher.RenderChunk vs$createRenderChunk(final int cx, final int cy, final int cz) {
        final int m = getChunkIndex(cx, cy, cz);
        // let's hope that m never gets accessed
        return vs$chunkRenderDispatcher.new RenderChunk(m, cx << 4, cy << 4, cz << 4);
    }

    @Inject(at = @At("HEAD"), method = "createChunks")
    void createChunks(final CallbackInfo ci) {
        if (vs$extraChunks != null) {
            vs$extraChunks.replaceAll((chunk) ->
                new VsViewArea.ExtraChunk(
                    chunk.cx(), chunk.cy(), chunk.cz(),
                    vs$createRenderChunk(chunk.cx(), chunk.cy(), chunk.cz())));
        }
    }

    @Inject(at = @At("HEAD"), method = "releaseAllBuffers")
    void releaseAllBuffers(final CallbackInfo ci) {
        if (vs$extraChunks != null) {
            vs$extraChunks.forEach((x) ->
                x.chunk().releaseBuffers());
        }
    }

    @Unique
    private void vs$repositionCameraFor(VsViewArea.ExtraChunk chunk) {
        int i = Mth.ceil(vs$camX);
        int j = Mth.ceil(vs$camZ);

        int l = getChunkGridSizeX() * 16;
        int m = i - 8 - l / 2;
        int n = m + Math.floorMod(chunk.cx() << 4 - m, l);

        int p = getChunkGridSizeZ() * 16;
        int q = j - 8 - p / 2;
        int r = q + Math.floorMod(chunk.cz() << 4 - q, p);

        int t = getLevel().getMinBuildHeight() + chunk.cy() << 4;
        BlockPos blockPos = chunk.chunk().getOrigin();
        if (n != blockPos.getX() || t != blockPos.getY() || r != blockPos.getZ()) {
            chunk.chunk().setOrigin(n, t, r);
        }
    }

    @Inject(at = @At("HEAD"), method = "repositionCamera")
    void repositionCamera(double d, double e, final CallbackInfo ci) {
        if (vs$extraChunks == null) return;

        vs$camX = d;
        vs$camZ = e;
        vs$didSetCam = true;

        for (final var chunk : vs$extraChunks) {
            vs$repositionCameraFor(chunk);
        }
    }

    @Inject(at = @At("HEAD"), method = "setDirty", cancellable = true)
    void setDirty(final int x, final int y, final int z, final boolean dirty, final CallbackInfo ci) {
        final int idx = vs$findChunk(x, y, z);
        if (idx != -1) {
            assert vs$extraChunks != null;
            vs$extraChunks.get(idx).chunk().setDirty(true);
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "getRenderChunkAt", cancellable = true)
    void getRenderChunkAt(final BlockPos blockPos, final CallbackInfoReturnable<RenderChunk> cir) {
        final int cx = blockPos.getX() >> 4;
        final int cy = blockPos.getY() >> 4;
        final int cz = blockPos.getZ() >> 4;

        final int idx = vs$findChunk(cx, cy, cz);
        if (idx != -1) {
            assert vs$extraChunks != null;
            cir.setReturnValue(vs$extraChunks.get(idx).chunk());
        }
    }

    @Shadow
    protected abstract int getChunkIndex(int cx, int cy, int cz);

    @Accessor
    abstract int getChunkGridSizeX();

    @Accessor
    abstract int getChunkGridSizeY();

    @Accessor
    abstract int getChunkGridSizeZ();

    @Accessor
    abstract Level getLevel();

    @Override
    @Nullable
    public ChunkRenderDispatcher.RenderChunk vs$addExtra(final int cx, final int cy, final int cz) {
        if (cx >= getChunkGridSizeX() || cy >= getChunkGridSizeY() || cz >= getChunkGridSizeZ()) {
            if (vs$extraChunks == null) {
                vs$extraChunks = new ObjectArrayList<>();
            }

            final var chunk = vs$createRenderChunk(cx, cy, cz);
            final var extra = new VsViewArea.ExtraChunk(cx, cy, cz, chunk);
            vs$extraChunks.add(extra);

            if (vs$didSetCam) {
                vs$repositionCameraFor(extra);
            }

            return chunk;
        }

        return getRenderChunkAt(new BlockPos(cx << 4, cy << 4, cz << 4));
    }

    @Override
    public void vs$clearExtra() {
        if (vs$extraChunks != null) {
            vs$extraChunks.clear();
        }
    }
}
