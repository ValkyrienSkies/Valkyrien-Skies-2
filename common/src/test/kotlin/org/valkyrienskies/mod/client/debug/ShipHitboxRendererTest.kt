package org.valkyrienskies.mod.client.debug

import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.bodies.ClientVsBody
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.api.render.ShipRenderView
import org.valkyrienskies.mod.api_impl.events.VsApiImpl
import org.valkyrienskies.mod.client.render.ShipRenderViews
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import kotlin.math.roundToInt

class ShipHitboxRendererTest {
    @Test
    fun worldPassDrawsStandaloneBodiesOnlyInTheActiveDimension() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val level = mockk<ClientLevel>(moreInterfaces = arrayOf(DimensionIdProvider::class))
        every { (level as DimensionIdProvider).dimensionId } returns "test:camera"
        val view = mockk<ShipRenderView> {
            every { this@mockk.level } returns level
            every { camera.position } returns Vec3.ZERO
        }
        fun body(id: Long, dimension: String) = mockk<ClientVsBody> {
            every { this@mockk.id } returns id
            every { this@mockk.dimension } returns dimension
        }
        val owned = body(3L, "test:camera")
        val other = body(4L, "test:other")
        val standalone = body(5L, "test:camera")
        every { standalone.renderAABB } returns AABBd(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
        every { standalone.renderTransform.toWorld } returns Matrix4d()
        every { standalone.renderTransform.toModel } returns Matrix4d()
        every { standalone.renderTransform.rotation } returns Quaterniond()
        every { standalone.renderTransform.positionInModel } returns Vector3d()
        every { standalone.collisionShape } returns null
        val ship = mockk<ClientShip> { every { bodyId } returns 3L }
        val world = mockk<VsiClientShipWorld> {
            every { loadedShips.iterator() } answers { listOf(ship).iterator() }
            every { allBodies.iterator() } answers { listOf(owned, other, standalone).iterator() }
        }
        val client = mockk<Minecraft>()
        client.player = mockk { every { isSpectator } returns true }
        val builder = BufferBuilder(1024)
        val buffers = mockk<MultiBufferSource.BufferSource> { every { getBuffer(any()) } returns builder }
        mockkStatic(Minecraft::class)
        try {
            every { Minecraft.getInstance() } returns client
            builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
            ShipHitboxRenderer.renderStandaloneBodies(view, world, PoseStack(), buffers)
            builder.end().also { assertTrue(it.drawState().vertexCount() > 0) }.release()
            verify(exactly = 0) { owned.renderTransform }
            verify(exactly = 0) { other.renderTransform }
        } finally {
            unmockkStatic(Minecraft::class)
        }
    }

    @Test
    fun toggleDrawsWorldAndShipBoxesInTheirOwnCoordinates() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val client = mockk<Minecraft>()
        client.player = mockk { every { isCreative } returns true }
        every { client.player!!.isSpectator } returns false
        val base = 28_000_000
        val ship = mockk<ClientShip> {
            every { id } returns 1L
            every { bodyId } returns null
            every { renderAABB } returns AABBd(101.0, 0.0, 0.0, 105.0, 1.0, 1.0)
            every { shipAABB } returns AABBi(base + 1, 2, 0, base + 3, 4, 1)
            every { renderTransform.positionInModel } returns Vector3d(base + 2.0, 3.0, 0.5)
        }
        val poses = PoseStack().apply {
            translate(7.0, 4.0, 0.0)
            mulPose(Quaternionf().rotationZ(Math.PI.toFloat() / 2))
            scale(2f, 3f, 1f)
        }
        val event = mockk<PostRenderShipEvent> {
            every { this@mockk.ship } returns ship
            every { origin } returns BlockPos(base, 0, 0)
            every { poseStack } returns poses
            every { view.viewMatrix } returns Matrix4f().translation(3f, 0f, 0f)
            every { view.camera.position } returns Vec3(100.0, 0.0, 0.0)
        }
        val requestedLayers = mutableSetOf<RenderType>()
        fun draw(): Map<Int, List<Int>> {
            val builder = BufferBuilder(1024)
            val format = DefaultVertexFormat.POSITION_COLOR_NORMAL
            requestedLayers.clear()
            every { event.buffers } returns MultiBufferSource { layer ->
                requestedLayers.add(layer)
                builder
            }
            builder.begin(VertexFormat.Mode.LINES, format)
            ShipRenderViews.overlays.emit(event)
            val batch = builder.endOrDiscardIfEmpty() ?: return emptyMap()
            try {
                val vertices = batch.vertexBuffer()
                return (0 until batch.drawState().vertexCount()).groupBy({ i ->
                    vertices.get(i * format.vertexSize + 12).toInt() and 255
                }, { i -> vertices.getFloat(i * format.vertexSize).roundToInt() })
            } finally {
                batch.release()
            }
        }
        mockkStatic(Minecraft::class)
        mockkStatic(::vsApi)
        try {
            every { Minecraft.getInstance() } returns client
            every { vsApi } returns VsApiImpl(mockk())
            every { client.showOnlyReducedInfo() } returns false
            ShipHitboxRenderer.setEnabled(true)
            val first = draw()
            assertEquals(setOf(4, 8), first.getValue(234).toSet())
            assertEquals(setOf(-5, 1), first.getValue(127).toSet())
            val xrayLines = requestedLayers.single { it !== RenderType.lines() }
            for (mode in GameType.values()) {
                every { client.player!!.isCreative } returns (mode == GameType.CREATIVE)
                every { client.player!!.isSpectator } returns (mode == GameType.SPECTATOR)
                assertEquals(first, draw(), mode.name)
                val expectedLayers = when (mode) {
                    GameType.CREATIVE, GameType.SPECTATOR -> setOf(RenderType.lines(), xrayLines)
                    else -> setOf(RenderType.lines())
                }
                assertEquals(expectedLayers, requestedLayers, mode.name)
            }
            ShipHitboxRenderer.setEnabled(true)
            assertEquals(first, draw())
            every { client.showOnlyReducedInfo() } returns true
            assertTrue(draw().isEmpty())
            every { client.showOnlyReducedInfo() } returns false
            ShipHitboxRenderer.setEnabled(false)
            assertTrue(draw().isEmpty())
        } finally {
            ShipHitboxRenderer.setEnabled(false)
            unmockkStatic(::vsApi)
            unmockkStatic(Minecraft::class)
        }
    }
}
