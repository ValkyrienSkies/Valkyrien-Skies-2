package org.valkyrienskies.mod.client.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexSorting
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import net.minecraft.SharedConstants
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.api.VsApi
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.render.ShipGeometryMask
import org.valkyrienskies.mod.api.render.ShipRenderView
import org.valkyrienskies.mod.api_impl.events.VsApiImpl
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer
import java.util.Optional

class ShipOverlayTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun overlaysUseTheViewVisibilityAndPreserveSubBlockPrecisionAcrossNestedRenders() {
        val api: VsApi = VsApiImpl(mockk())
        val level = mockk<ClientLevel>(moreInterfaces = arrayOf(DimensionIdProvider::class))
        every { (level as DimensionIdProvider).dimensionId } returns "test"
        val frustum = mockk<Frustum>()
        every { frustum.isVisible(any()) } answers { firstArg<AABB>().minX == 0.0 }
        fun viewAt(x: Double): ShipRenderView {
            val camera = mockk<Camera>()
            every { camera.position } returns Vec3(x, 0.0, 0.0)
            return ShipRenderView(level, camera, Matrix4f(), Matrix4f(), frustum, 0.5f)
        }
        val main = viewAt(12.25)
        val monitor = viewAt(15.75)
        val center = 28_000_000.5
        fun ship(dimension: String, minX: Double) = mockk<ClientShip> {
            every { chunkClaimDimension } returns dimension
            every { renderAABB } returns AABBd(minX, 0.0, 0.0, minX + 1, 1.0, 1.0)
            every { renderTransform.positionInModel } returns Vector3d(center, 0.0, 0.0)
            every { renderTransform.shipToWorld } returns Matrix4d().translation(13.0 - center, 0.0, 0.0).scale(1.0, 2.0, 1.0)
        }
        val hull = ship("test", 0.0)
        val cloaked = ship("test", 0.0)
        val ships = listOf(hull, cloaked, ship("test", 100.0), ship("other", 0.0))
        val poses = PoseStack().apply { translate(2.0, 0.0, 0.0) }
        val calls = mutableListOf<Pair<ShipRenderView, ClientShip>>()
        val masks = mutableMapOf<ShipRenderView, ShipGeometryMask>()
        fun checkMask(event: PostRenderShipEvent) {
            if (event.ship !== hull) return
            val mask = event.geometryMask()
            assertSame(masks.getValue(event.view), mask)
        }
        var outerEvent: PostRenderShipEvent? = null
        var outerBuffers: MultiBufferSource? = null
        val batches = mutableListOf<List<Float>>()
        val layer = mockk<RenderType>()
        every { layer.asOptional() } returns Optional.of(layer)
        every { layer.canConsolidateConsecutiveGeometry() } returns true
        every { layer.mode() } returns VertexFormat.Mode.TRIANGLES
        every { layer.format() } returns DefaultVertexFormat.POSITION
        every { layer.end(any(), any()) } answers {
            val batch = firstArg<BufferBuilder>().end()
            try {
                val vertices = batch.vertexBuffer()
                batches.add(List(batch.drawState().vertexCount()) { vertices.getFloat(it * DefaultVertexFormat.POSITION.vertexSize) })
            } finally {
                batch.release()
            }
        }
        val visibility = api.registerShipRenderPredicate { candidate, view -> candidate !== cloaked || view === monitor }
        val overlays = api.postRenderShipEvent.on { event ->
            calls.add(event.view to event.ship)
            assertSame(event.view, api.currentShipRenderView)
            assertEquals(BlockPos(28_000_000, 0, 0), event.origin)
            val expected = 2.0 + 12.5 - event.view.camera.position.x
            assertEquals(expected.toFloat(), event.poseStack.last().pose().m30(), 0.00001f)
            assertEquals(0.5f, event.poseStack.last().normal().m11(), 0.00001f)
            val buffer = event.buffers.getBuffer(layer)
            val matrix = event.poseStack.last().pose()
            buffer.vertex(matrix, 0f, 0f, 0f).endVertex()
            buffer.vertex(matrix, 1f, 0f, 0f).endVertex()
            buffer.vertex(matrix, 0f, 1f, 0f).endVertex()
            checkMask(event)
            event.poseStack.translate(99.0, 0.0, 0.0)
            if (event.view === main) {
                outerEvent = event
                outerBuffers = event.buffers
                ShipRenderViews.render(monitor, { ships }) {
                    assertNull(outerEvent!!.geometryMask())
                    ShipRenderViews.recordTerrain(poses, Matrix4f(), 0.0, 0.0, 0.0)
                    ShipRenderViews.renderOverlays(poses)
                }
                assertSame(main, api.currentShipRenderView)
            } else {
                assertNotSame(outerBuffers, event.buffers)
            }
        }
        val second = api.postRenderShipEvent.on { event -> checkMask(event) }
        val renderer = ShipBatchRenderer.INSTANCE
        mockkObject(renderer)
        mockkStatic(RenderSystem::class)
        try {
            every { renderer.geometryMask(any(), any(), any(), any()) } answers {
                assertSame(hull, secondArg<ClientShip>())
                val view = thirdArg<ShipRenderView>()
                assertFalse(masks.containsKey(view))
                ShipGeometryMask(masks.size + 1, 8, 8, 0, 0, 8, 8).also { masks[view] = it }
            }
            every { RenderSystem.getVertexSorting() } returns VertexSorting.DISTANCE_TO_ORIGIN
            every { RenderSystem.getShaderTexture(any()) } returns 0
            every { RenderSystem.getShaderColor() } returns floatArrayOf(1f, 1f, 1f, 1f)
            ShipRenderViews.render(main, { ships }) {
                ShipRenderViews.recordTerrain(poses, Matrix4f(), 0.0, 0.0, 0.0)
                repeat(2) { ShipRenderViews.renderOverlays(poses) }
            }
            assertNull(outerEvent!!.geometryMask())
            assertEquals(2, masks.size)
            assertEquals(listOf(main to hull, monitor to hull, monitor to cloaked), calls)
            assertEquals(listOf(listOf(-1.25f, -0.25f, -1.25f, -1.25f, -0.25f, -1.25f),
                listOf(2.25f, 3.25f, 2.25f)), batches)
            assertEquals(2.0f, poses.last().pose().m30())
        } finally {
            overlays.unregister()
            second.unregister()
            unmockkObject(renderer)
            visibility.unregister()
            unmockkStatic(RenderSystem::class)
        }
    }
}
