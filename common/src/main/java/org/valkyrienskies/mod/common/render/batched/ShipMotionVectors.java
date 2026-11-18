package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * An optional second colour attachment carrying screen-space motion vectors for ship geometry, for
 * an addon that wants to blur a ship that is moving (Torque blurs its cars with it).
 *
 * <p>The vectors are produced by the ship vertex shaders as the difference between where a vertex is
 * this frame and where it was last frame, both seen through this frame's camera -- so the camera's
 * own movement cancels and what survives is the ship's motion <em>relative to the frame the viewer
 * is being carried along in</em>, which {@link #setViewerFrame} declares. On the ground that is the
 * world and a moving ship is what blurs; riding a ship it is that ship, which therefore produces
 * exactly zero, and what blurs is everything else. {@code .b} is 1 for ship geometry and {@code .a}
 * is the fragment's depth, which lets a consumer reject a ship pixel that something nearer has
 * since drawn over.
 *
 * <p>Every renderer a ship can be drawn by produces them: the batched renderer through
 * {@link ShipBatchRenderer}, and sodium's chunk renderer through the compat layer's own ship
 * program. Under Iris there are none, because Iris owns the ship pass there and VS stands down from
 * it entirely; {@link #begin()} declines as well, so nothing is written into a pack's g-buffer.
 *
 * <h2>Two switches, and they are not the same switch</h2>
 *
 * <p>{@link #setEnabled} is a declaration and has to be made before a world is meshed.
 * Sodium's mesher packs VS's own flags into the vertex alpha only when a ship feature is on, and
 * VS's ship program is the only thing that can read them back -- so the program and the packing have
 * to agree, and a shipyard chunk meshed before the declaration would be drawn by a shader expecting
 * bits that are not there. Flipping it therefore re-meshes the world, exactly as VS's other ship
 * shader features do.
 *
 * <p>{@link #setTexture} is the per-frame handover of somewhere to put them, and is expected once a
 * frame before the level draws. It also marks the buffer for clearing and steps the frame counter
 * the history below is keyed on.
 *
 * <h2>Why the attachment is scoped</h2>
 *
 * <p>It is bound around the ship draws and nothing else, and that is load-bearing rather than
 * tidiness. A fragment shader that does not write an output for which a draw buffer is bound leaves
 * undefined values there, and Minecraft's forward renderer draws entities, block entities, particles
 * and the sky into the same framebuffer with shaders nobody here controls. Left bound for the whole
 * level pass, the buffer fills with whatever those shaders happen to leave in a register. Bound only
 * while ship geometry is drawn, every write to it comes from a shader that means one, and everything
 * else keeps the zero it was cleared to.
 */
public final class ShipMotionVectors {

    private static final int[] COLOUR_AND_MOTION = {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1};
    private static final int[] COLOUR_ONLY = {GL30.GL_COLOR_ATTACHMENT0};

    private static final float[] NOTHING = {0.0f, 0.0f, 0.0f, 0.0f};

    /** Ships not drawn for this many frames are forgotten. */
    private static final int HISTORY_GRACE = 4;

    private static boolean enabled;
    private static boolean inPass = true;
    private static int texture = 0;
    private static int attachedTo = 0;
    private static boolean needsClear;
    private static boolean collected;
    private static int frame;

    /**
     * Where each ship was a frame ago, in world terms, keyed by ship id.
     *
     * <p>{@code ClientShip.getPrevTickTransform()} is the obvious thing to reach for and is wrong:
     * it is a tick boundary, and the current transform is interpolated, so the difference ramps from
     * zero to a whole tick's travel within each tick and snaps back -- the blur pulses at 20 Hz.
     *
     * <p>Shared by every renderer, because only one of them draws a given ship in a frame, and
     * frame-stamped because sodium visits a ship once per render pass and must be told the same
     * thing all three times.
     */
    private static final class History {
        final Matrix4d previous = new Matrix4d();
        final Matrix4d current = new Matrix4d();
        /** {@link #previous} seen from the frame the viewer rides; see {@link #setViewerFrame}. */
        final Matrix4d apparent = new Matrix4d();
        boolean primed;
        int frame = -1;
    }

    private static final Long2ObjectOpenHashMap<History> history = new Long2ObjectOpenHashMap<>();

    /**
     * How the world moved past the viewer over the last frame, as a world-space transform.
     *
     * <p>Identity for somebody standing on the ground. For somebody riding a ship it is
     * {@code S_now * S_prev^-1}, which is where a point that never moved appears to have been a
     * frame ago to an observer carried along by that ship -- see {@link #setViewerFrame}.
     */
    private static final Matrix4d viewerDelta = new Matrix4d();
    private static final Matrix4d viewerScratch = new Matrix4d();

    private static final LongOpenHashSet prepassSeen = new LongOpenHashSet();
    private static final LongOpenHashSet prepassPinned = new LongOpenHashSet();
    private static final Vector3d prepassCamScratch = new Vector3d();
    private static final Matrix4d prepassRenderScratch = new Matrix4d();
    private static final Matrix4f prepassRenderFloat = new Matrix4f();
    private static final Matrix4f prepassModelView = new Matrix4f();
    private static final Matrix4f prepassPrevious = new Matrix4f();
    private static int prepassShips;
    private static int prepassMeshes;
    private static int prepassDrawn;

    private ShipMotionVectors() {
    }

    /**
     * Declare that motion vectors are wanted. Re-meshes the world when it changes, so call it on
     * level load rather than once a frame -- see the note above about the mesher and the shader
     * having to agree.
     */
    public static void setEnabled(final boolean on) {
        if (enabled == on) {
            return;
        }
        enabled = on;
        history.clear();
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.levelRenderer != null && minecraft.level != null) {
            minecraft.levelRenderer.allChanged();
        }
    }

    /** Whether the ship shaders should be built to emit motion vectors. */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether vectors may be collected during the ship pass itself, as a second colour attachment.
     *
     * <p>False forces the {@link #prepass} everywhere, which is what a shader pack gets anyway. Its
     * reason for existing is that the two routes are otherwise only ever exercised by different
     * configurations, so a fault in the one that only runs under Iris cannot be told apart from
     * Iris -- with this the prepass can be measured against a backend whose numbers are known.
     */
    public static void setInPass(final boolean on) {
        inPass = on;
    }

    /**
     * Hand over the RGBA16F texture to receive motion vectors, or 0 for none. It must be the size of
     * the framebuffer the level is drawn into. Expected once a frame, before the level draws.
     */
    public static void setTexture(final int textureId) {
        texture = textureId;
        needsClear = textureId != 0;
        collected = false;
        frame++;
        // Back to the ground until told otherwise, so a consumer that stops declaring a viewer
        // frame goes back to blurring what moves rather than carrying yesterday's ship about.
        viewerDelta.identity();
        if (history.size() > 64) {
            history.values().removeIf(entry -> frame - entry.frame > HISTORY_GRACE);
        }
    }

    public static int getTexture() {
        return texture;
    }

    /**
     * Whether any ship geometry has been drawn into the buffer since it was handed over.
     *
     * <p>False means the buffer is still the zeroes it was cleared to, so there is nothing to
     * composite -- no ship in frame, or a shader pack that owns the ship pass. Worth asking,
     * because the alternative is a full-screen pass that provably cannot change a pixel.
     */
    public static boolean collected() {
        return collected;
    }

    /**
     * Declare the frame of reference the viewer is being carried along in: the ship they are riding
     * and where it is now, or {@code -1} and {@code null} for somebody standing on the ground.
     * Expected once a frame, beside {@link #setTexture} and before anything is drawn.
     *
     * <p>Motion blur is a difference and this is what it is a difference *from*. A vector is only
     * meaningful relative to an observer: to somebody on the ground a car is what moves, and to
     * somebody sitting in that car it is the world that moves and the car that is still. Both are
     * the same statement -- blur what is moving relative to the viewer -- and the only thing that
     * distinguishes them is which frame the viewer is in.
     *
     * <p>So this sets {@code D = S_now * S_prev^-1}, the transform that carries a point from where
     * it was a frame ago to where it must be drawn now to have stayed put relative to the viewer.
     * Every previous transform is then premultiplied by it, and three cases fall out of the one
     * expression without being special-cased anywhere:
     *
     * <ul>
     *   <li>The ship being ridden: {@code D * S_prev == S_now}, so it produces exactly zero. You
     *       are not moving relative to the car you are sitting in, and it does not blur.</li>
     *   <li>The static world: it has no ship transform, so its apparent previous position is
     *       {@code D} itself and it streams past. That is the blur of driving.</li>
     *   <li>Another ship: {@code S_k_now} against {@code D * S_k_prev}, which is the difference
     *       between the two ships' motion -- so a car keeping station beside you is sharp and one
     *       overtaking is smeared by exactly the amount it is overtaking by.</li>
     * </ul>
     */
    public static void setViewerFrame(final long shipId, final Matrix4dc shipToWorld) {
        if (shipId == -1L || shipToWorld == null) {
            viewerDelta.identity();
            return;
        }
        // Through the same history every ship goes through, so the ship being ridden cannot end up
        // with one idea of where it was here and a different one when it is drawn -- which would
        // leave the car the viewer is sitting in blurring slightly against itself.
        final History entry = roll(shipId, shipToWorld);
        viewerScratch.set(entry.previous).invert();
        viewerDelta.set(shipToWorld).mul(viewerScratch);
    }

    /** How the world moved past the viewer over the last frame. Identity on the ground. */
    public static Matrix4dc viewerDelta() {
        return viewerDelta;
    }

    /**
     * Where this ship's geometry appears to have been a frame ago to the viewer, given where it is
     * now. Records the current transform on the first call of each frame and answers the same thing
     * for the rest of it.
     *
     * <p>Appears to have been, rather than was: the answer is in the viewer's frame of reference,
     * so it is the raw previous transform premultiplied by {@link #setViewerFrame}'s delta. On the
     * ground that delta is the identity and the two are the same thing.
     *
     * <p>A ship with no history answers its current transform, so it reads as stationary for one
     * frame rather than smearing in from wherever the last ship in that slot happened to be.
     */
    public static Matrix4dc previousShipToWorld(final long shipId, final Matrix4dc current) {
        final History entry = roll(shipId, current);
        return entry.apparent.set(viewerDelta).mul(entry.previous);
    }

    /** Steps a ship's history to this frame, once however many times it is asked for. */
    private static History roll(final long shipId, final Matrix4dc current) {
        History entry = history.get(shipId);
        if (entry == null) {
            entry = new History();
            history.put(shipId, entry);
        }
        if (entry.frame != frame) {
            if (entry.primed) {
                entry.previous.set(entry.current);
            } else {
                entry.previous.set(current);
                entry.primed = true;
            }
            entry.current.set(current);
            entry.frame = frame;
        }
        return entry;
    }

    /**
     * Forget a ship's history. For a ship that has been culled or has gone: one that comes back
     * somewhere else would otherwise smear across everything between the two positions.
     */
    public static void forget(final long shipId) {
        history.remove(shipId);
    }

    /**
     * Draws every visible ship's geometry a second time, emitting only motion vectors, into
     * whatever framebuffer is bound.
     *
     * <p>For when something else owns the ship pass. A shader pack under Iris binds a g-buffer of
     * its own across it and fills every attachment from programs of its own, so there is nowhere to
     * hang a second colour target and no shader of ours in the pass to write one -- {@link #begin()}
     * declines for exactly that reason. Drawing the geometry again into a target of our own asks
     * nothing of whoever is drawing the world, which is the same trick, and the same reason, as
     * Torque's panel filter running offscreen rather than on the block face.
     *
     * <p>It costs a pass over ship geometry and nothing else: no terrain, no entities, opaque
     * layers only. The mesh is {@link ShipMeshCache}'s, shared with the batched renderer and the
     * portrait, so a ship one of those is already drawing is not meshed twice.
     *
     * <p>The caller is responsible for the depth buffer. Handing this one primed with the scene's
     * depth and a {@code GL_LEQUAL} test is what makes a car behind a wall stay behind it; handing
     * it an empty one draws every ship as though nothing were in front of it.
     *
     * @param baseModelView the world camera's view matrix, without any ship in it
     * @return how many vertex buffers were drawn, so a caller can tell "no ships" from "no vectors"
     */
    public static int prepass(final ClientLevel level, final ShaderInstance shader,
        final Matrix4f baseModelView, final Matrix4f projection,
        final double camX, final double camY, final double camZ, final Frustum frustum,
        final boolean depthTest) {

        RenderSystem.assertOnRenderThread();
        if (level == null || shader == null) {
            return 0;
        }
        final Uniform previousUniform = shader.getUniform("PreviousModelViewMat");
        final Uniform chunkOffset = shader.CHUNK_OFFSET;

        prepassSeen.clear();
        prepassShips = 0;
        prepassMeshes = 0;
        int drawn = 0;
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (frustum != null
                && !frustum.isVisible(VectorConversionsMCKt.toMinecraft(ship.getRenderAABB()))) {
                // Its history stops here, as it does for a ship the batched renderer culls: one
                // that comes back somewhere else would smear across everything in between.
                forget(ship.getId());
                continue;
            }

            prepassShips++;
            final ShipRenderObject object = ShipMeshCache.INSTANCE.obtain(ship);
            // Pinned because nothing else is keeping it: the renderer that would have retained this
            // ship is the one that has been stood down.
            ShipMeshCache.INSTANCE.pin(ship.getId());
            prepassSeen.add(ship.getId());
            ShipMeshCache.INSTANCE.compile(level, object, true);
            final ShipMesh mesh = object.getMesh();
            if (mesh == null) {
                continue;
            }
            prepassMeshes++;

            final ShipTransform transform = ship.getRenderTransform();
            prepassCamScratch.set(camX, camY, camZ).sub(transform.getPosition());
            transform.getRotation().transformInverse(prepassCamScratch);
            final Vector3dc scaling = transform.getScaling();
            prepassCamScratch.x /= scaling.x();
            prepassCamScratch.y /= scaling.y();
            prepassCamScratch.z /= scaling.z();
            prepassCamScratch.add(transform.getPositionInModel());

            prepassRenderScratch
                .translation(-camX, -camY, -camZ)
                .mul(transform.getShipToWorld())
                .translate(prepassCamScratch.x, prepassCamScratch.y, prepassCamScratch.z);
            prepassRenderFloat.set(prepassRenderScratch);
            prepassModelView.set(baseModelView).mul(prepassRenderFloat);

            // Same camera and same vertex origin, only the ship put back where it was a frame ago.
            prepassRenderScratch
                .translation(-camX, -camY, -camZ)
                .mul(previousShipToWorld(ship.getId(), transform.getShipToWorld()))
                .translate(prepassCamScratch.x, prepassCamScratch.y, prepassCamScratch.z);
            prepassRenderFloat.set(prepassRenderScratch);
            prepassPrevious.set(baseModelView).mul(prepassRenderFloat);
            if (previousUniform != null) {
                previousUniform.set(prepassPrevious);
            }

            for (int layer = 0; layer < ShipSectionMesh.CHUNK_LAYERS.length; layer++) {
                final RenderType renderType = ShipSectionMesh.CHUNK_LAYERS[layer];
                if (renderType == RenderType.translucent()) {
                    // Nothing behind glass is being blurred, and a translucent layer would write a
                    // vector over whatever it is in front of.
                    continue;
                }
                final VertexBuffer buffer = mesh.getOpaque(layer);
                if (buffer == null) {
                    continue;
                }
                // For the block atlas and the depth state; the shader is overridden below, and
                // drawWithShader is what actually binds it.
                renderType.setupRenderState();
                // After setupRenderState, not before: it sets both of these itself, so anything the
                // caller arranged beforehand is overwritten between one layer and the next.
                // Depth writing must be off because the depth being tested against is the caller's
                // copy of the scene's, and the composite compares against that same copy after.
                RenderSystem.depthFunc(depthTest ? GL11.GL_LEQUAL : GL11.GL_ALWAYS);
                RenderSystem.depthMask(false);
                if (chunkOffset != null) {
                    chunkOffset.set(
                        (float) (mesh.refX - prepassCamScratch.x),
                        (float) (mesh.refY - prepassCamScratch.y),
                        (float) (mesh.refZ - prepassCamScratch.z));
                }
                buffer.bind();
                buffer.drawWithShader(new Matrix4f(prepassModelView), projection, shader);
                VertexBuffer.unbind();
                renderType.clearRenderState();
                drawn++;
            }
        }

        // Let go of anything this pass did not draw, or the cache grows a ship at a time.
        if (!prepassPinned.isEmpty()) {
            prepassPinned.removeIf((long id) -> {
                if (prepassSeen.contains(id)) {
                    return false;
                }
                ShipMeshCache.INSTANCE.unpin(id);
                return true;
            });
        }
        prepassPinned.addAll(prepassSeen);
        prepassDrawn = drawn;
        if (drawn > 0) {
            collected = true;
        }
        return drawn;
    }

    /** Ships the last prepass looked at, after frustum culling. */
    public static int prepassShips() {
        return prepassShips;
    }

    /** How many of those had geometry compiled and ready. */
    public static int prepassMeshes() {
        return prepassMeshes;
    }

    /** Vertex buffers the last prepass actually drew. */
    public static int prepassDrawn() {
        return prepassDrawn;
    }

    /** Attach the motion texture to whatever framebuffer is bound. True if it took. */
    public static boolean begin() {
        if (texture == 0 || !inPass) {
            return false;
        }
        final int framebuffer = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer == 0) {
            // The default framebuffer has no attachment points to add to.
            return false;
        }
        // Stand down unless the framebuffer is drawing exactly one colour attachment, which is what
        // Minecraft's own renderer does. Iris and any shader pack under it bind a g-buffer of
        // several attachments here and expect the ship pass to fill all of them; replacing that
        // list with a list of ours would be writing the pack's normals and lighting terms with
        // whatever a shader that knows nothing about them happens to leave behind. Testing the
        // state rather than looking for a mod by name means anything else that arranges its own
        // targets gets the same courtesy without this having to have heard of it.
        if (GL11.glGetInteger(GL30.GL_DRAW_BUFFER0) != GL30.GL_COLOR_ATTACHMENT0
            || GL11.glGetInteger(GL30.GL_DRAW_BUFFER1) != GL11.GL_NONE) {
            return false;
        }
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
            GL11.GL_TEXTURE_2D, texture, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL11.GL_TEXTURE_2D, 0, 0);
            // A mismatched texture would otherwise take the whole ship pass down with it.
            texture = 0;
            return false;
        }
        GL30.glDrawBuffers(COLOUR_AND_MOTION);
        attachedTo = framebuffer;
        collected = true;
        if (needsClear) {
            needsClear = false;
            // Draw buffer 1 alone, so the scene already drawn into attachment 0 survives. Scissor
            // would clip a clear, and everything outside the box would keep the last frame's
            // vectors -- which reads as a band of the screen smearing on its own.
            final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (scissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            // Tracked, so Minecraft's idea of the mask stays true; every render type ship
            // geometry is drawn under has all four channels open anyway, so this is a no-op.
            RenderSystem.colorMask(true, true, true, true);
            GL30.glClearBufferfv(GL11.GL_COLOR, 1, NOTHING);
            if (scissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        return true;
    }

    /** Put the framebuffer back the way Minecraft expects to find it. */
    public static void end() {
        if (attachedTo == 0) {
            return;
        }
        // Detaching has to happen on the framebuffer it was attached to, whatever is bound now.
        final int current = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (current != attachedTo) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, attachedTo);
        }
        GL30.glDrawBuffers(COLOUR_ONLY);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
            GL11.GL_TEXTURE_2D, 0, 0);
        if (current != attachedTo) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, current);
        }
        attachedTo = 0;
    }
}
