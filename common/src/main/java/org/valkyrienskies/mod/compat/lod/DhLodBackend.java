package org.valkyrienskies.mod.compat.lod;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthDirection;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFarFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiAfterRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;

/**
 * Distant Horizons. DH draws its LODs into its own framebuffer before Minecraft draws terrain (Forge: at the start of
 * the solid layer; Fabric: after world render setup), so its depth texture is complete when ships are drawn.
 */
final class DhLodBackend implements LodRenderBackend {

    private final LodDepth projection = new LodDepth();
    private boolean projectionValid;
    private long renderedFrame = Long.MIN_VALUE;

    DhLodBackend() {
        final DhApiResult<Void> before = DhApiEventRegister.on(DhApiBeforeRenderEvent.class,
            new DhApiBeforeRenderEvent() {
                @Override
                public void beforeRender(final DhApiCancelableEventParam<DhApiRenderParam> event) {
                    final DhApiRenderParam param = event == null ? null : event.value;
                    final DhApiMat4f matrix = param == null ? null : param.dhProjectionMatrix;
                    projectionValid = matrix != null && projection.setProjection(matrix.m22, matrix.m23, matrix.m32);
                    if (event != null && !event.isEventCanceled()) {
                        renderedFrame = LodCompat.currentFrame();
                    }
                }
            });
        final DhApiResult<Void> after = DhApiEventRegister.on(DhApiAfterRenderEvent.class,
            new DhApiAfterRenderEvent() {
                @Override
                public void afterRender(final DhApiEventParam<Void> event) {
                    renderedFrame = LodCompat.currentFrame();
                }
            });
        if (before == null || !before.success || after == null || !after.success) {
            throw new IllegalStateException("Distant Horizons refused the render event listeners: "
                + (before == null ? "null" : before.message) + ", " + (after == null ? "null" : after.message));
        }
    }

    @Override
    public String name() {
        return "Distant Horizons";
    }

    @Override
    public int renderDistanceBlocks() {
        final IDhApiConfig configs = DhApi.Delayed.configs;
        if (configs == null) {
            return 0;
        }
        final IDhApiGraphicsConfig graphics = configs.graphics();
        if (!Boolean.TRUE.equals(graphics.renderingEnabled().getValue())) {
            return 0;
        }
        final Integer chunks = graphics.chunkRenderDistance().getValue();
        return chunks == null ? 0 : Math.max(0, chunks) * 16;
    }

    String lastFailure = "";

    @Override
    public boolean depthForThisFrame(final LodDepth out) {
        if (renderedFrame != LodCompat.currentFrame()) {
            lastFailure = "DH did not render this frame";
            return false;
        }
        if (!projectionValid) {
            lastFailure = "DH projection matrix is not a perspective projection";
            return false;
        }
        final IDhApiRenderProxy proxy = DhApi.Delayed.renderProxy;
        if (proxy == null) {
            lastFailure = "DH render proxy not ready";
            return false;
        }
        final DhApiResult<Integer> texture = proxy.getDhDepthTextureGlId();
        if (texture == null || !texture.success || texture.payload == null || texture.payload <= 0) {
            lastFailure = "no DH depth texture: " + (texture == null ? "null" : texture.message);
            return false;
        }
        lastFailure = "";
        out.textureId = texture.payload;
        out.projectionA = projection.projectionA;
        out.projectionB = projection.projectionB;
        if (proxy.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE) {
            out.ndcScale = 1.0f;
            out.ndcBias = 0.0f;
        } else {
            out.ndcScale = 2.0f;
            out.ndcBias = -1.0f;
        }
        final EDhApiDepthDirection direction = proxy.getDepthDirection();
        out.emptyDepth = direction == null ? 1.0f : direction.farDepth;
        return true;
    }

    /**
     * DH's "vanilla fade" pass replaces every Minecraft pixel beyond {@code (near clip + 16) * 1.5} blocks with the
     * LOD color behind it, fully from {@code * 1.9}. It runs after the opaque layers and again after the
     * translucent ones. The fade mode is not in DH's API, so assume it is on whenever DH drew this frame.
     */
    @Override
    public float overpaintDistanceBlocks(final float partialTick) {
        if (renderedFrame != LodCompat.currentFrame()) {
            return Float.POSITIVE_INFINITY;
        }
        final IDhApiRenderProxy proxy = DhApi.Delayed.renderProxy;
        if (proxy == null) {
            return Float.POSITIVE_INFINITY;
        }
        return (proxy.getNearClipPlaneDistanceInBlocks(partialTick) + 16.0f) * 1.5f;
    }

    @Override
    public boolean fogFractions(final float[] out) {
        final IDhApiConfig configs = DhApi.Delayed.configs;
        if (configs == null) {
            return false;
        }
        final IDhApiFogConfig fog = configs.graphics().fog();
        if (!Boolean.TRUE.equals(fog.enableDhFog().getValue())) {
            return false;
        }
        final IDhApiFarFogConfig farFog = fog.farFog();
        final Float start = farFog.farFogStartDistance().getValue();
        final Float end = farFog.farFogEndDistance().getValue();
        if (start == null || end == null) {
            return false;
        }
        out[0] = Math.max(0.0f, start);
        out[1] = Math.max(out[0] + 0.001f, end);
        return true;
    }
}
