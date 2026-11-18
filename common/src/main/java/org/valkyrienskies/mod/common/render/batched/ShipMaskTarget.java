package org.valkyrienskies.mod.common.render.batched;

import java.util.ArrayDeque;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.primitives.AABBdc;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.system.MemoryStack;
import org.valkyrienskies.mod.api.render.ShipGeometryMask;
import org.valkyrienskies.mod.client.render.ShipMaskTerrain;
import static org.lwjgl.opengl.GL33C.*;

public final class ShipMaskTarget {
    private static final ArrayDeque<ShipMaskTarget> pool = new ArrayDeque<>();
    private static int generation;
    private final int created = generation;
    private int framebuffer, texture, width, height;
    private Rect dirty;

    public static ShipMaskTarget acquire() {
        return pool.isEmpty() ? new ShipMaskTarget() : pool.removeFirst();
    }

    public void release() {
        if (created == generation) pool.addFirst(this);
        else delete();
    }

    static void clearPool() {
        generation++;
        while (!pool.isEmpty()) pool.removeFirst().delete();
        ShipMaskDraw.releaseSampler();
    }

    private void delete() {
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(texture);
    }

    record Rect(int x, int y, int width, int height) {
        Rect intersect(final Rect other) {
            final int left = Math.max(x, other.x), bottom = Math.max(y, other.y);
            return new Rect(left, bottom, Math.min(x + width, other.x + other.width) - left,
                Math.min(y + height, other.y + other.height) - bottom);
        }

        static Rect read(final int name) {
            final int[] v = new int[4];
            glGetIntegerv(name, v);
            return new Rect(v[0], v[1], v[2], v[3]);
        }

        void scissor() { glScissor(x, y, width, height); }
    }

    static Rect bounds(final ShipMaskTerrain terrain, final AABBdc box, final Rect viewport) {
        final Matrix4f clip = new Matrix4f(terrain.getProjection()).mul(terrain.getModelView());
        float left = 1, right = -1, bottom = 1, top = -1;
        final Vector4f p = new Vector4f();
        for (int i = 0; i < 8; i++) {
            clip.transform(p.set((float) (((i & 1) == 0 ? box.minX() : box.maxX()) - terrain.getX()),
                (float) (((i & 2) == 0 ? box.minY() : box.maxY()) - terrain.getY()),
                (float) (((i & 4) == 0 ? box.minZ() : box.maxZ()) - terrain.getZ()), 1f));
            if (p.w <= 0f) return viewport;
            left = Math.min(left, p.x / p.w);
            right = Math.max(right, p.x / p.w);
            bottom = Math.min(bottom, p.y / p.w);
            top = Math.max(top, p.y / p.w);
        }
        final int x = viewport.x + (int) Math.floor((Math.max(-1, left) + 1) * 0.5 * viewport.width) - 1;
        final int y = viewport.y + (int) Math.floor((Math.max(-1, bottom) + 1) * 0.5 * viewport.height) - 1;
        final int endX = viewport.x + (int) Math.ceil((Math.min(1, right) + 1) * 0.5 * viewport.width) + 1;
        final int endY = viewport.y + (int) Math.ceil((Math.min(1, top) + 1) * 0.5 * viewport.height) + 1;
        return viewport.intersect(new Rect(x, y, endX - x, endY - y));
    }

    private record Attachment(int type, int name, int level, int face, int layer) {
        static Attachment read(final int point) {
            final int type = parameter(point, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type == GL_NONE) return new Attachment(type, 0, 0, 0, 0);
            final int name = parameter(point, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            if (type != GL_TEXTURE) return new Attachment(type, name, 0, 0, 0);
            if (parameter(point, GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0) return null;
            return new Attachment(type, name, parameter(point, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL),
                parameter(point, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE),
                parameter(point, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER));
        }

        private static int parameter(final int point, final int name) {
            return glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, point, name);
        }

        void attach(final int point) {
            if (type == GL_RENDERBUFFER) glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, point, GL_RENDERBUFFER, name);
            else if (face != 0) glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, point, face, name, level);
            else if (layer != 0) glFramebufferTextureLayer(GL_DRAW_FRAMEBUFFER, point, name, level, layer);
            else {
                glFramebufferTexture(GL_DRAW_FRAMEBUFFER, point, name, level);
                if (name != 0 && parameter(point, GL_FRAMEBUFFER_ATTACHMENT_LAYERED) != 0) {
                    glFramebufferTextureLayer(GL_DRAW_FRAMEBUFFER, point, name, level, 0);
                }
            }
        }
    }

    private static void blendEquation(final int rgb, final int alpha) {
        final var gl = GL.getCapabilities();
        if (gl.OpenGL40) GL40C.glBlendEquationSeparatei(0, rgb, alpha);
        else if (gl.GL_ARB_draw_buffers_blend) ARBDrawBuffersBlend.glBlendEquationSeparateiARB(0, rgb, alpha);
        else glBlendEquationSeparate(rgb, alpha);
    }

    ShipGeometryMask render(final Rect viewport, Rect area, final Runnable draw) {
        final int scene = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        if (scene == 0 || glGetInteger(GL_SAMPLE_BUFFERS) != 0) return null;
        final Attachment depth = Attachment.read(GL_DEPTH_ATTACHMENT);
        if (depth == null || depth.type == GL_NONE) return null;
        final Attachment stencil = Attachment.read(GL_STENCIL_ATTACHMENT);
        if (stencil == null) return null;
        final boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        final Rect savedScissor = Rect.read(GL_SCISSOR_BOX);
        if (scissor) area = area.intersect(savedScissor);
        final int w = viewport.x + viewport.width, h = viewport.y + viewport.height;
        area = area.intersect(new Rect(0, 0, w, h));
        if (area.width <= 0 || area.height <= 0) return null;

        final boolean depthTest = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE);
        final int depthFunc = glGetInteger(GL_DEPTH_FUNC), cullFace = glGetInteger(GL_CULL_FACE_MODE);
        final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        final int front = glGetInteger(GL_STENCIL_WRITEMASK), back = glGetInteger(GL_STENCIL_BACK_WRITEMASK);
        final boolean blend = glIsEnabledi(GL_BLEND, 0);
        final boolean logicOp = glIsEnabled(GL_COLOR_LOGIC_OP);
        final int rgb = glGetInteger(GL_BLEND_EQUATION_RGB), alpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var channels = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, channels);
            if (framebuffer == 0) framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            try {
                allocate(w, h);
                glEnable(GL_SCISSOR_TEST);
                glColorMaski(0, true, true, true, true);
                if (dirty != null) {
                    dirty.scissor();
                    glClearBufferfv(GL_COLOR, 0, stack.floats(0f, 0f, 0f, 0f));
                }
                depth.attach(GL_DEPTH_ATTACHMENT);
                stencil.attach(GL_STENCIL_ATTACHMENT);
                if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) return null;
                dirty = area;
                area.scissor();
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
                glDepthMask(false);
                glStencilMask(0);
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
                glEnablei(GL_BLEND, 0);
                if (logicOp) glDisable(GL_COLOR_LOGIC_OP);
                blendEquation(GL_MAX, GL_MAX);
                draw.run();
                return new ShipGeometryMask(texture, width, height, area.x, area.y, area.width, area.height);
            } finally {
                glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, 0, 0);
                glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, 0, 0);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene);
                blendEquation(rgb, alpha);
                if (logicOp) glEnable(GL_COLOR_LOGIC_OP);
                if (!blend) glDisablei(GL_BLEND, 0);
                glColorMaski(0, channels.get(0) != 0, channels.get(1) != 0, channels.get(2) != 0, channels.get(3) != 0);
                glStencilMaskSeparate(GL_FRONT, front);
                glStencilMaskSeparate(GL_BACK, back);
                glDepthMask(depthWrite);
                glDepthFunc(depthFunc);
                if (!depthTest) glDisable(GL_DEPTH_TEST);
                glCullFace(cullFace);
                if (!cull) glDisable(GL_CULL_FACE);
                savedScissor.scissor();
                if (!scissor) glDisable(GL_SCISSOR_TEST);
            }
        }
    }

    private void allocate(final int w, final int h) {
        if (width == w && height == h) return;
        final int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        final int unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (texture == 0) texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
        glBindTexture(GL_TEXTURE_2D, previous);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        width = w;
        height = h;
        dirty = new Rect(0, 0, w, h);
    }
}
