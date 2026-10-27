package org.valkyrienskies.mod.client.debug

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.world.phys.AABB
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.physics.VsiDebugVector
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.debug.DebugRendererMode
import org.valkyrienskies.mod.common.debug.LatestValueTask
import org.valkyrienskies.mod.common.networking.DebugShipInfo
import org.valkyrienskies.mod.common.networking.PacketPhysicsDebug
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

object ShipDebugRenderer {
    private data class Label(val text: String, val position: Vector3d, val color: Int)
    private data class Update(val packet: PacketPhysicsDebug, val source: ClientPacketListener, val receivedAt: Long)
    private var packet = PacketPhysicsDebug()
    private var connection: ClientPacketListener? = null
    private var receivedAt = 0L
    private val labelCache = HashMap<Long, List<String>>()
    private var nextLabelUpdate = 0L
    private val updates = LatestValueTask<Update>({ Minecraft.getInstance().execute(it) }) {
        if (Minecraft.getInstance().connection === it.source) accept(it)
    }

    fun enqueue(value: PacketPhysicsDebug, source: ClientPacketListener) {
        updates.offer(Update(value, source, System.nanoTime()))
    }

    private fun accept(update: Update) {
        if (packet.mode != update.packet.mode || connection !== update.source || packet.dimensionId != update.packet.dimensionId) {
            labelCache.clear()
            nextLabelUpdate = 0L
        }
        packet = update.packet
        connection = update.source
        receivedAt = update.receivedAt
    }

    @JvmStatic
    fun clear() {
        packet = PacketPhysicsDebug()
        connection = null
        updates.clear()
        labelCache.clear()
        nextLabelUpdate = 0L
    }

    private fun active(): Boolean {
        val minecraft = Minecraft.getInstance()
        return packet.mode != DebugRendererMode.NONE && connection != null && connection === minecraft.connection &&
            minecraft.level?.dimensionId == packet.dimensionId
    }

    @JvmStatic
    fun renderHud(graphics: GuiGraphics) {
        if (!active() || Minecraft.getInstance().options.hideGui) return
        val minecraft = Minecraft.getInstance()
        val text = buildList {
            add("VS debug renderer: ${packet.mode.commandName} | /vs debug renderer none")
            if (packet.mode.showMarkers) {
                add("Green: awake | Blue: asleep | Orange: fragment | Grey: static")
                add("Yellow: applied force | Red: gravity | Cyan: drag")
                add("Violet: lift and wings | White: velocity | Pink: torque | Lime: net estimate")
                add("Red points: contacts | Gold lines: joints | RGB: ship axes")
            }
            add("${packet.ships.size} ships | Sample ${packet.physicsTick}" +
                (if (packet.mode.showMarkers) " | ${packet.contacts.size} contacts | ${packet.joints.size} joints" else "") +
                (if (packet.limited) " | Data limit reached" else ""))
            if (packet.mode.showMarkers) add("Arrows use a log scale.")
            if (packet.mode.showText) add("Numbers use N, N m, kg, m, and s.")
        }
        var y = graphics.guiHeight() - text.size * 10 - 6
        for (line in text) {
            graphics.fill(4, y - 1, 8 + minecraft.font.width(line), y + 9, 0xA0000000.toInt())
            graphics.drawString(minecraft.font, line, 6, y, 0xFFFFFF)
            y += 10
        }
        if (System.nanoTime() - receivedAt > 2_000_000_000L) {
            graphics.drawString(minecraft.font, "Physics data is old.", 6, y, 0xFF5555)
        }
    }

    @JvmStatic
    fun render(matrices: PoseStack, buffers: MultiBufferSource.BufferSource,
        cameraX: Double, cameraY: Double, cameraZ: Double) {
        if (!active() || Minecraft.getInstance().showOnlyReducedInfo()) return
        val world = Minecraft.getInstance().level.shipObjectWorld
        val camera = Vector3d(cameraX, cameraY, cameraZ)
        val labelsToRender = ArrayList<Label>()
        val now = System.nanoTime()
        if (packet.mode.showText && now >= nextLabelUpdate) {
            labelCache.clear()
            nextLabelUpdate = now + 50_000_000L
        }
        matrices.pushPose()
        try {
            val consumer = if (packet.mode.showMarkers) buffers.getBuffer(RenderType.lines()) else null
            for (info in packet.ships) {
                val ship = world.loadedShips.getById(info.physics.id) ?: continue
                val bounds = ship.renderAABB
                val center = Vector3d(ship.renderTransform.position)
                if (!center.isFinite) continue
                val validBox = bounds.minX() <= bounds.maxX() && bounds.minY() <= bounds.maxY() && bounds.minZ() <= bounds.maxZ() &&
                    bounds.minX().isFinite() && bounds.minY().isFinite() && bounds.minZ().isFinite() &&
                    bounds.maxX().isFinite() && bounds.maxY().isFinite() && bounds.maxZ().isFinite()
                val box = if (validBox) bounds else org.joml.primitives.AABBd(
                    center.x - 0.25, center.y - 0.25, center.z - 0.25,
                    center.x + 0.25, center.y + 0.25, center.z + 0.25)
                val color = when {
                    info.physics.isStatic -> 0x999999
                    info.physics.sleeping -> 0x5599FF
                    info.parentShipId != null -> 0xFF9933
                    else -> 0x55FF77
                }
                if (consumer != null) {
                    LevelRenderer.renderLineBox(matrices, consumer,
                        AABB(box.minX() - cameraX, box.minY() - cameraY, box.minZ() - cameraZ,
                            box.maxX() - cameraX, box.maxY() - cameraY, box.maxZ() - cameraZ),
                        red(color), green(color), blue(color), 1.0f)
                    point(matrices, consumer, center, camera, color, 0.15)
                    if (info.parentShipId != null) point(matrices, consumer, center, camera, 0xFF9933, 0.25)
                    for ((axis, axisColor) in listOf(Vector3d(1.0, 0.0, 0.0) to 0xFF5555,
                        Vector3d(0.0, 1.0, 0.0) to 0x55FF55, Vector3d(0.0, 0.0, 1.0) to 0x5555FF)) {
                        ship.renderTransform.rotation.transform(axis)
                        line(matrices, consumer, center, Vector3d(center).add(axis), camera, axisColor)
                    }
                    val physics = info.physics
                    arrow(matrices, consumer, center, vector(physics.appliedForce), camera, 0xFFFF55)
                    arrow(matrices, consumer, center, vector(physics.gravityForce), camera, 0xFF5555)
                    arrow(matrices, consumer, center, vector(physics.dragForce), camera, 0x55FFFF)
                    arrow(matrices, consumer, center, vector(physics.liftForce).add(vector(physics.wingForce)), camera, 0xBB77FF)
                    arrow(matrices, consumer, center, vector(physics.velocity), camera, 0xFFFFFF)
                    arrow(matrices, consumer, center, vector(physics.appliedTorque), camera, 0xFF77BB)
                    arrow(matrices, consumer, center, vector(physics.netForceEstimate), camera, 0xAAFF55)
                    for (force in physics.forces) {
                        val position = ship.renderTransform.shipToWorld.transformPosition(vector(force.positionInModel))
                        arrow(matrices, consumer, position, vector(force.force), camera, 0xFFDD33)
                    }
                }
                if (packet.mode.showText) {
                    val labelY = box.maxY() + 0.5
                    val lines = labelCache.getOrPut(ship.id) { labels(info, ship) }
                    for ((index, text) in lines.withIndex()) {
                        labelsToRender.add(Label(text,
                            Vector3d(center.x, labelY + (lines.size - index) * 0.22, center.z), color))
                    }
                }
            }
            if (consumer != null) {
                for (contact in packet.contacts) {
                    val point = vector(contact.position)
                    point(matrices, consumer, point, camera, 0xFF5555, 0.08)
                    line(matrices, consumer, point, Vector3d(point).fma(0.6, vector(contact.normal)), camera, 0xFF5555)
                }
                for (joint in packet.joints) {
                    val point0 = renderEndpoint(world.loadedShips.getById(joint.bodyId0 ?: -1L), joint.position0, joint.positionInModel0)
                    val point1 = renderEndpoint(world.loadedShips.getById(joint.bodyId1 ?: -1L), joint.position1, joint.positionInModel1)
                    line(matrices, consumer, point0, point1, camera, 0xFFCC55)
                    point(matrices, consumer, point0, camera, 0xFFCC55, 0.1)
                    point(matrices, consumer, point1, camera, 0xFFCC55, 0.1)
                    val middle = Vector3d(point0).add(point1).mul(0.5)
                    labelsToRender.add(Label("Joint ${joint.id}: ${joint.type}", middle.add(0.0, 0.2, 0.0), 0xFFCC55))
                }
                for (origin in packet.origins) {
                    val position = vector(origin)
                    if (position.distanceSquared(camera) <= 128.0 * 128.0) {
                        point(matrices, consumer, position, camera, 0xFFFFFF, 0.5)
                        labelsToRender.add(Label("Physics origin", position.add(0.0, 0.7, 0.0), 0xFFFFFF))
                    }
                }
            }
            for (label in labelsToRender) {
                DebugRenderer.renderFloatingText(matrices, buffers, label.text,
                    label.position.x, label.position.y, label.position.z, label.color, 0.02f)
            }
        } finally {
            matrices.popPose()
        }
    }

    private fun labels(info: DebugShipInfo, ship: ClientShip): List<String> {
        val physics = info.physics
        val state = if (physics.isStatic) "static" else if (physics.sleeping) "asleep" else "awake"
        val originDistance = packet.origins.minOfOrNull { vector(it).distance(ship.renderTransform.position) }
        val ageLabel = if (info.creationTimeKnown) "Age" else "Tracked age"
        return listOf(
            "Ship ${ship.id} ${ship.slug ?: ""} | $state" + (info.parentShipId?.let { " | Fragment of $it" } ?: ""),
            "Mass ${number(physics.mass)} kg | Weight ${number(physics.weight)} N | $ageLabel ${number(info.ageTicks / 20.0)} s",
            "Speed ${number(length(physics.velocity))} m/s | Spin ${number(length(physics.angularVelocity))} rad/s",
            "Applied ${number(length(physics.appliedForce))} N | Net estimate ${number(length(physics.netForceEstimate))} N | Torque ${number(length(physics.appliedTorque))} N m",
            "Drag ${number(length(physics.dragForce))} N | Lift ${number(length(physics.liftForce))} N | Wings ${number(length(physics.wingForce))} N",
            "Nearest origin ${originDistance?.let(::number) ?: "unknown"} m",
        )
    }

    private fun renderEndpoint(ship: ClientShip?, position: VsiDebugVector, modelPosition: VsiDebugVector?) =
        if (ship == null || modelPosition == null) vector(position)
        else ship.renderTransform.shipToWorld.transformPosition(vector(modelPosition))

    private fun vector(value: VsiDebugVector) = Vector3d(value.x, value.y, value.z)
    private fun length(value: VsiDebugVector) = sqrt(value.x * value.x + value.y * value.y + value.z * value.z)
    private fun number(value: Double) = String.format(Locale.ROOT, if (kotlin.math.abs(value) >= 100000) "%.2e" else "%.2f", value)
    private fun red(color: Int) = ((color shr 16) and 255) / 255.0f
    private fun green(color: Int) = ((color shr 8) and 255) / 255.0f
    private fun blue(color: Int) = (color and 255) / 255.0f

    private fun point(matrices: PoseStack, consumer: VertexConsumer, position: Vector3d,
        camera: Vector3d, color: Int, radius: Double) {
        val p = Vector3d(position).sub(camera)
        LevelRenderer.renderLineBox(matrices, consumer,
            AABB(p.x - radius, p.y - radius, p.z - radius, p.x + radius, p.y + radius, p.z + radius),
            red(color), green(color), blue(color), 1.0f)
    }

    private fun arrow(matrices: PoseStack, consumer: VertexConsumer, start: Vector3d,
        value: Vector3d, camera: Vector3d, color: Int) {
        val magnitude = value.length()
        if (!magnitude.isFinite() || magnitude < 1e-8) return
        val direction = Vector3d(value).div(magnitude)
        val end = Vector3d(start).fma((ln(1.0 + magnitude) * 0.6).coerceIn(0.15, 12.0), direction)
        line(matrices, consumer, start, end, camera, color)
        val side = direction.cross(if (kotlin.math.abs(direction.y) < 0.9) Vector3d(0.0, 1.0, 0.0)
            else Vector3d(1.0, 0.0, 0.0), Vector3d()).normalize().mul(0.15)
        val base = Vector3d(end).fma(-0.3, direction)
        line(matrices, consumer, end, Vector3d(base).add(side), camera, color)
        line(matrices, consumer, end, Vector3d(base).sub(side), camera, color)
    }

    private fun line(matrices: PoseStack, consumer: VertexConsumer, from: Vector3d,
        to: Vector3d, camera: Vector3d, color: Int) {
        val direction = Vector3d(to).sub(from)
        if (!direction.isFinite || direction.lengthSquared() < 1e-12) return
        direction.normalize()
        val pose = matrices.last()
        for (position in listOf(from, to)) {
            consumer.vertex(pose.pose(), (position.x - camera.x).toFloat(),
                (position.y - camera.y).toFloat(), (position.z - camera.z).toFloat())
                .color(red(color), green(color), blue(color), 1.0f)
                .normal(pose.normal(), direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
                .endVertex()
        }
    }
}
