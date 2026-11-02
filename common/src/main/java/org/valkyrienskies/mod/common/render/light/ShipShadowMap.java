package org.valkyrienskies.mod.common.render.light;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL33.*;

/** Render all ship boxes into a shared depth texture. Restore the caller's OpenGL state. */
public final class ShipShadowMap implements AutoCloseable {
    public static final int TEXTURE_UNIT = 14;
    private static final int COMPARISON_TEXTURE_UNIT = 15;
    private int comparisonSampler;
    private int texture;
    private int framebuffer;
    private int program;
    private int vertices;
    private int instances;
    private int indices;
    private int transforms;
    private int transformTexture;
    private int heightUniform;
    private int cascadeUniform;
    private int localUniform;
    private int transformsUniform;
    private boolean localVertices;

    public void render(final ByteBuffer boxes, final int count, final ShipShadowMapLayout layout) {
        render(boxes, null, count, layout, false);
    }

    public void renderLocal(final ByteBuffer boxes, final ByteBuffer matrices, final int count,
        final ShipShadowMapLayout layout) {
        render(boxes, matrices, count, layout, true);
    }

    private void render(final ByteBuffer boxes, final ByteBuffer matrices, final int count,
        final ShipShadowMapLayout layout, final boolean local) {
        final int layers = boxes != null || matrices != null || texture == 0
            ? (1 << ShipShadowMapLayout.CASCADES) - 1 : layout.changedLayers;
        if (layers == 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
            final int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            final int oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            final int oldVertices = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            final int oldBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
            final int oldUnpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
            final int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
            final int oldTransformTexture = glGetInteger(GL_TEXTURE_BINDING_BUFFER);
            final int oldDepthFunc = glGetInteger(GL_DEPTH_FUNC);
            final boolean oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
            final double oldClearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
            final var viewport = stack.mallocInt(4);
            final var depthRange = stack.mallocDouble(2);
            final var polygonMode = stack.mallocInt(2);
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetDoublev(GL_DEPTH_RANGE, depthRange);
            glGetIntegerv(GL_POLYGON_MODE, polygonMode);
            final int[] caps = {GL_DEPTH_TEST, GL_CULL_FACE, GL_SCISSOR_TEST, GL_POLYGON_OFFSET_FILL,
                GL_RASTERIZER_DISCARD, GL_DEPTH_CLAMP, GL_STENCIL_TEST, GL_SAMPLE_COVERAGE,
                GL_SAMPLE_ALPHA_TO_COVERAGE, GL_SAMPLE_MASK,
                GL_CLIP_DISTANCE0, GL_CLIP_DISTANCE1, GL_CLIP_DISTANCE2, GL_CLIP_DISTANCE3,
                GL_CLIP_DISTANCE4, GL_CLIP_DISTANCE5, GL_CLIP_DISTANCE6, GL_CLIP_DISTANCE7};
            final boolean[] enabled = new boolean[caps.length];
            for (int i = 0; i < caps.length; i++) enabled[i] = glIsEnabled(caps[i]);
            try {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                ensureResources();
                glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
                glViewport(0, 0, ShipShadowMapLayout.RESOLUTION, ShipShadowMapLayout.RESOLUTION);
                glEnable(GL_DEPTH_TEST);
                for (int i = 1; i < caps.length; i++) glDisable(caps[i]);
                glDepthFunc(GL_LESS);
                glDepthMask(true);
                glClearDepth(1.0);
                glDepthRange(0.0, 1.0);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glUseProgram(program);
                glUniform1i(localUniform, local ? 1 : 0);
                glUniform1i(transformsUniform, glGetInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_BUFFER, transformTexture);
                if (matrices != null) {
                    glBindBuffer(GL_ARRAY_BUFFER, transforms);
                    glBufferData(GL_ARRAY_BUFFER, matrices, GL_STREAM_DRAW);
                    glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, transforms);
                }
                glBindVertexArray(vertices);
                glBindBuffer(GL_ARRAY_BUFFER, instances);
                if (localVertices != local) {
                    for (int i = 0; i < 3; i++) {
                        glVertexAttribPointer(i, 4, GL_FLOAT, false, local ? 32 : 48,
                            local && i == 2 ? 16L : i * 16L);
                    }
                    localVertices = local;
                }
                // A null input reuses the last instance upload after camera motion.
                if (boxes != null) glBufferData(GL_ARRAY_BUFFER, boxes, GL_STREAM_DRAW);
                glUniform2f(heightUniform, layout.top, layout.range);
                for (int layer = 0; layer < ShipShadowMapLayout.CASCADES; layer++) {
                    if ((layers & (1 << layer)) == 0) continue;
                    glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, texture, 0, layer);
                    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                        throw new IllegalStateException("Ship shadow framebuffer is incomplete");
                    }
                    glClear(GL_DEPTH_BUFFER_BIT);
                    final int i = layer * 4;
                    glUniform3f(cascadeUniform,
                        layout.cascades[i], layout.cascades[i + 1], layout.cascades[i + 2]);
                    glDrawElementsInstanced(GL_TRIANGLES, 36, GL_UNSIGNED_INT, 0L, count);
                }
            } finally {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
                glUseProgram(oldProgram);
                glBindVertexArray(oldVertices);
                glBindBuffer(GL_ARRAY_BUFFER, oldBuffer);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, oldUnpackBuffer);
                glBindTexture(GL_TEXTURE_2D_ARRAY, oldTexture);
                glBindTexture(GL_TEXTURE_BUFFER, oldTransformTexture);
                glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                glDepthFunc(oldDepthFunc);
                glDepthMask(oldDepthMask);
                glClearDepth(oldClearDepth);
                glDepthRange(depthRange.get(0), depthRange.get(1));
                glPolygonMode(GL_FRONT_AND_BACK, polygonMode.get(0));
                for (int i = 0; i < caps.length; i++) {
                    if (enabled[i]) glEnable(caps[i]); else glDisable(caps[i]);
                }
            }
        }
    }

    public void bind(final int receiver, final ShipShadowMapLayout layout, final boolean enabled) {
        final int sampler = glGetUniformLocation(receiver, "u_VsShipShadowMap");
        if (sampler < 0) return;
        final int active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0 + TEXTURE_UNIT);
        glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        glActiveTexture(GL_TEXTURE0 + COMPARISON_TEXTURE_UNIT);
        glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        glBindSampler(COMPARISON_TEXTURE_UNIT, comparisonSampler);
        glActiveTexture(active);
        glUniform1i(sampler, TEXTURE_UNIT);
        glUniform1i(glGetUniformLocation(receiver, "u_VsShipShadowCompare"), COMPARISON_TEXTURE_UNIT);
        glUniform1i(glGetUniformLocation(receiver, "u_VsShipShadowMapEnabled"), enabled && texture != 0 ? 1 : 0);
        glUniform3f(glGetUniformLocation(receiver, "u_VsShipShadowOrigin"),
            (float) layout.originX, (float) layout.originY, (float) layout.originZ);
        glUniform2f(glGetUniformLocation(receiver, "u_VsShipShadowHeight"), layout.top, layout.range);
        glUniform4fv(glGetUniformLocation(receiver, "u_VsShipShadowCascades"), layout.cascades);
    }

    private void ensureResources() {
        if (texture != 0) return;
        // Compile first. A shader error must not leave a partially initialized map.
        final int vertex = compile(GL_VERTEX_SHADER, "ship_shadow_depth.vsh");
        final int fragment;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, "ship_shadow_depth.fsh");
        } catch (RuntimeException error) {
            glDeleteShader(vertex);
            throw error;
        }
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            final String log = glGetProgramInfoLog(program);
            close();
            throw new IllegalStateException("Cannot link ship shadow shader: " + log);
        }
        texture = glGenTextures();
        heightUniform = glGetUniformLocation(program, "Height");
        cascadeUniform = glGetUniformLocation(program, "Cascade");
        localUniform = glGetUniformLocation(program, "LocalSpace");
        transformsUniform = glGetUniformLocation(program, "ShipTransforms");
        transforms = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, transforms);
        glBufferData(GL_ARRAY_BUFFER, 48L, GL_STREAM_DRAW);
        transformTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_BUFFER, transformTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, transforms);
        glBindTexture(GL_TEXTURE_2D_ARRAY, texture);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT32F,
            ShipShadowMapLayout.RESOLUTION, ShipShadowMapLayout.RESOLUTION, ShipShadowMapLayout.CASCADES,
            0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        comparisonSampler = glGenSamplers();
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glSamplerParameteri(comparisonSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        vertices = glGenVertexArrays();
        instances = glGenBuffers();
        glBindVertexArray(vertices);
        indices = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, new int[]{
            0, 2, 3, 0, 3, 1, 4, 5, 7, 4, 7, 6,
            0, 1, 5, 0, 5, 4, 2, 6, 7, 2, 7, 3,
            0, 4, 6, 0, 6, 2, 1, 3, 7, 1, 7, 5}, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, instances);
        for (int i = 0; i < 3; i++) {
            glEnableVertexAttribArray(i);
            glVertexAttribPointer(i, 4, GL_FLOAT, false, 48, i * 16L);
            glVertexAttribDivisor(i, 1);
        }
    }

    private static int compile(final int type, final String name) {
        final String path = "/assets/valkyrienskies/shaders/shadow/" + name;
        final String source;
        try (var stream = ShipShadowMap.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing " + path);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read ship shadow shader", error);
        }
        final int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            final String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Cannot compile " + name + ": " + log);
        }
        return shader;
    }

    @Override
    public void close() {
        if (texture != 0) glDeleteTextures(texture);
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (program != 0) glDeleteProgram(program);
        if (vertices != 0) glDeleteVertexArrays(vertices);
        if (instances != 0) glDeleteBuffers(instances);
        if (indices != 0) glDeleteBuffers(indices);
        if (transforms != 0) glDeleteBuffers(transforms);
        if (transformTexture != 0) glDeleteTextures(transformTexture);
        if (comparisonSampler != 0) glDeleteSamplers(comparisonSampler);
        texture = framebuffer = program = vertices = instances = indices = 0;
        transforms = transformTexture = 0;
        comparisonSampler = 0;
        localVertices = false;
    }
}
