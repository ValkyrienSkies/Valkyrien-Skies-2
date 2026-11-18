package org.valkyrienskies.mod.client.render

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.BlockPos
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.valkyrienskies.core.api.event.RegisteredListener
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.render.ShipRenderPredicate
import org.valkyrienskies.mod.api.render.ShipMaterialSelection
import org.valkyrienskies.mod.api.render.ShipMaterialSelector
import org.valkyrienskies.mod.api.render.ShipRenderView
import org.valkyrienskies.mod.common.VSClientGameUtils
import org.valkyrienskies.mod.api.render.ShipGeometryMask
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer
import org.valkyrienskies.mod.common.render.batched.ShipMaskTarget
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.common.config.ShipRenderer
import org.valkyrienskies.mod.common.config.shipRenderer
import org.valkyrienskies.mod.common.render.ShipRenderConfig
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier

object ShipRenderViews {
    private class Frame(val view: ShipRenderView, val ships: Supplier<Iterable<ClientShip>>) {
        var hidden: Set<ClientShip> = emptySet()
        var materialTable: ShipMaterialTable? = null
        var materials: Map<ClientShip, ShipMaterialStack> = emptyMap()
        var renderedOverlays = false
        var nativeMask: ShipTerrainMaskRenderer? = null
        var terrain: ShipMaskTerrain? = null
        var maskTargets: MutableList<ShipMaskTarget>? = null

        fun visibleShips(): List<ClientShip> {
            val dimension = view.level.dimensionId
            return ships.get().filter { ship ->
                ship !in hidden && ship.chunkClaimDimension == dimension && view.frustum.isVisible(ship.renderAABB.toMinecraft())
            }
        }
    }

    internal val overlays = EventEmitterImpl<PostRenderShipEvent>()
    private val overlayBuffers = ArrayDeque<MultiBufferSource.BufferSource>()
    private val predicates = CopyOnWriteArrayList<ShipRenderPredicate>()
    private val selectors = CopyOnWriteArrayList<ShipMaterialSelector>()
    private val active = ThreadLocal<Frame>()

    @JvmStatic val current get() = active.get()?.view
    @JvmStatic val cameraPosition get() = (current?.camera ?: Minecraft.getInstance().gameRenderer.mainCamera).position
    @JvmStatic val renderLevel get() = current?.level ?: Minecraft.getInstance().level

    @get:JvmStatic @get:JvmName("getMaterialTable")
    internal val materialTable get() = active.get()?.materialTable

    @JvmStatic fun hasHiddenShips(): Boolean = active.get()?.hidden?.isNotEmpty() == true

    fun register(predicate: ShipRenderPredicate): RegisteredListener {
        val registration = ShipRenderPredicate(predicate::shouldRenderShip)
        predicates.add(registration)
        return RegisteredListener { predicates.remove(registration) }
    }

    @JvmStatic
    fun shouldRenderShip(ship: ClientShip): Boolean = active.get()?.hidden?.contains(ship) != true

    @JvmStatic fun hasMaterialSelectors(): Boolean = selectors.isNotEmpty()

    fun registerSelector(selector: ShipMaterialSelector): RegisteredListener {
        val listener = ShipMaterialSelector(selector::selectShipMaterial)
        selectors.add(listener)
        return RegisteredListener { selectors.remove(listener) }
    }

    @JvmStatic
    @JvmName("materialsFor")
    internal fun materialsFor(ship: ClientShip): ShipMaterialStack? = active.get()?.materials?.get(ship)

    @JvmStatic fun hasOverlayListeners(): Boolean = overlays.hasListeners

    @JvmStatic
    @JvmName("recordNativeTerrain")
    internal fun recordNativeTerrain(pose: PoseStack, projection: Matrix4f, x: Double, y: Double, z: Double,
                                    renderer: ShipTerrainMaskRenderer) {
        recordTerrain(pose, projection, x, y, z)
        active.get()?.nativeMask = renderer
    }

    @JvmStatic
    fun recordTerrain(pose: PoseStack, projection: Matrix4f, x: Double, y: Double, z: Double) {
        val frame = active.get() ?: return
        if (overlays.hasListeners && frame.terrain == null) {
            frame.terrain = ShipMaskTerrain(pose, projection, x, y, z)
        }
    }

    @JvmStatic
    fun render(view: ShipRenderView, ships: Supplier<Iterable<ClientShip>>, draw: Runnable) {
        val outer = active.get()
        val frame = Frame(view, ships)
        active.set(frame)
        try {
            val materialSelectors = if (selectors.isEmpty()) null else selectors.toTypedArray()
            if (predicates.isNotEmpty()) {
                val filters = predicates.toTypedArray()
                frame.hidden = ships.get().filterTo(HashSet()) { ship ->
                    filters.any { !it.shouldRenderShip(ship, view) }
                }
            }
            if (materialSelectors != null) {
                val table = ShipMaterialTable()
                val selections = ArrayList<ShipMaterialSelection>(materialSelectors.size)
                frame.materials = buildMap {
                    for (ship in frame.visibleShips()) {
                        selections.clear()
                        materialSelectors.mapNotNullTo(selections) { it.selectShipMaterial(ship, view) }
                        if (selections.isNotEmpty()) put(ship, table.append(selections))
                    }
                }
                if (frame.materials.isNotEmpty()) frame.materialTable = table
            }
            draw.run()
        } finally {
            frame.maskTargets?.forEach { it.release() }
            if (outer == null) active.remove() else active.set(outer)
        }
    }

    @JvmStatic
    fun renderOverlays(worldPose: PoseStack) {
        val frame = active.get() ?: return
        if (frame.renderedOverlays || !overlays.hasListeners) return
        frame.renderedOverlays = true
        val view = frame.view
        val visible = frame.visibleShips()
        if (visible.isEmpty()) return

        val buffers = overlayBuffers.pollFirst() ?: MultiBufferSource.immediate(BufferBuilder(256))
        val camera = view.camera.position
        for (ship in visible) {
            val transform = ship.renderTransform
            val center = transform.positionInModel
            val origin = BlockPos.containing(center.x(), center.y(), center.z())
            val poses = PoseStack()
            val pose = poses.last().pose().set(worldPose.last().pose())
            VSClientGameUtils.transformRenderWithShip(transform, pose, origin.x.toDouble(), origin.y.toDouble(),
                origin.z.toDouble(), camera.x, camera.y, camera.z)
            val normal = Matrix4f(transform.shipToWorld).normal(Matrix3f())
            poses.last().normal().set(worldPose.last().normal()).mul(normal)
            val event = Overlay(ship, view, origin, poses, buffers, frame)
            try {
                overlays.emit(event)
            } finally {
                event.open = false
            }
        }
        buffers.endBatch()
        overlayBuffers.addFirst(buffers)
    }

    private class Overlay(
        override val ship: ClientShip,
        override val view: ShipRenderView,
        override val origin: BlockPos,
        override val poseStack: PoseStack,
        override val buffers: MultiBufferSource,
        val frame: Frame
    ) : PostRenderShipEvent {
        var open = true
        private var requested = false
        private var mask: ShipGeometryMask? = null

        override fun geometryMask(): ShipGeometryMask? {
            if (!open || active.get() !== frame) return null
            if (requested) return mask
            requested = true
            val terrain = frame.terrain ?: return null
            val target = ShipMaskTarget.acquire()
            try {
                val native = frame.nativeMask
                val renderer = if (native == null) ShipRenderer.BATCHED else ship.shipRenderer
                mask = if (native == null || renderer == ShipRenderer.BATCHED) {
                    ShipBatchRenderer.INSTANCE.geometryMask(target, ship, view, terrain)
                } else {
                    if (renderer == ShipRenderer.FLYWHEEL) {
                        ShipRenderConfig.requestFlywheelMask(view.level)
                        if (!ShipRenderConfig.useTerrainForFlywheelHull()) return null
                    }
                    native.render(target, ship, terrain)
                }
                if (mask != null) {
                    val targets = frame.maskTargets ?: ArrayList<ShipMaskTarget>().also { frame.maskTargets = it }
                    targets.add(target)
                }
                return mask
            } finally {
                if (mask == null) target.release()
            }
        }
    }
}
