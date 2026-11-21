package org.valkyrienskies.mod.client.render

import io.mockk.mockk
import io.mockk.every
import net.minecraft.SharedConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import org.joml.primitives.AABBd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.api.VsApi
import org.valkyrienskies.mod.api.render.ShipMaterial
import org.valkyrienskies.mod.api.render.ShipMaterialPass
import org.valkyrienskies.mod.api.render.ShipMaterialSelection
import org.valkyrienskies.mod.api.render.ShipRenderView
import org.valkyrienskies.mod.api_impl.events.VsApiImpl
import org.valkyrienskies.mod.common.util.DimensionIdProvider

class ShipRenderViewsTest {
    private val api: VsApi = VsApiImpl(mockk())

    @Test
    fun materialSelectionIsOrderedAndScopedToVisibleShipsInEachView() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val level = mockk<ClientLevel>(moreInterfaces = arrayOf(DimensionIdProvider::class))
        every { (level as DimensionIdProvider).dimensionId } returns "test"
        fun view() = mockk<ShipRenderView> {
            every { this@mockk.level } returns level
            every { frustum.isVisible(any()) } answers { firstArg<AABB>().minX == 0.0 }
        }
        val outer = view()
        val inner = view()
        val hidden = mockk<ClientShip>()
        fun hull(x: Double) = mockk<ClientShip> {
            every { chunkClaimDimension } returns "test"
            every { renderAABB } returns AABBd(x, 0.0, 0.0, x + 1, 1.0, 1.0)
        }
        val ship = hull(0.0)
        val outside = hull(10.0)
        val material = ShipMaterialSelection(ShipMaterial(ResourceLocation("test:effect"), 1))
        val other = material.copy(x = 0.5f, pass = ShipMaterialPass.BLENDED)
        val calls = mutableListOf<ShipRenderView>()
        val filter = api.registerShipRenderPredicate { candidate, _ -> candidate !== hidden }
        val first = api.registerShipMaterialSelector { candidate, view ->
            assertSame(ship, candidate)
            calls.add(view)
            material.takeIf { view === outer }
        }
        val second = api.registerShipMaterialSelector { _, _ -> other }
        try {
            ShipRenderViews.render(outer, { listOf(ship, hidden, outside) }) {
                assertNull(ShipRenderViews.materialsFor(hidden))
                assertNull(ShipRenderViews.materialsFor(outside))
                val stack = requireNotNull(ShipRenderViews.materialsFor(ship))
                assertEquals(2, stack.count)
                assertTrue(stack.blended)
                ShipRenderViews.render(inner, { listOf(ship) }) {
                    assertEquals(1, ShipRenderViews.materialsFor(ship)?.count)
                    first.unregister()
                    second.unregister()
                }
                assertSame(stack, ShipRenderViews.materialsFor(ship))
            }
            assertEquals(listOf(outer, inner), calls)
            assertNull(ShipRenderViews.materialsFor(ship))
        } finally {
            second.unregister()
            first.unregister()
            filter.unregister()
        }
    }

    @Test
    fun unregisteringAFilterAffectsTheNextView() {
        val view = mockk<ShipRenderView>()
        val hidden = mockk<ClientShip>()
        val visible = mockk<ClientShip>()
        val ships = listOf(hidden, visible)
        val filter = api.registerShipRenderPredicate { ship, _ -> ship !== hidden }
        try {
            ShipRenderViews.render(view, { ships }) {
                filter.unregister()
                assertFalse(ShipRenderViews.shouldRenderShip(hidden))
                assertTrue(ShipRenderViews.shouldRenderShip(visible))
            }
            ShipRenderViews.render(view, { ships }) {
                assertTrue(ShipRenderViews.shouldRenderShip(hidden))
            }
        } finally {
            filter.unregister()
        }
    }

    @Test
    fun nestedCameraHasItsOwnDecisionAndRestoresTheOuterViewAfterFailure() {
        val mainLevel = mockk<ClientLevel>()
        val otherLevel = mockk<ClientLevel>()
        val main = mockk<ShipRenderView> {
            every { this@mockk.camera.position } returns Vec3(1.0, 2.0, 3.0)
            every { level } returns mainLevel
        }
        val camera = mockk<ShipRenderView> {
            every { this@mockk.camera.position } returns Vec3(1000.0, 2.0, 3.0)
            every { level } returns otherLevel
        }
        val ship = mockk<ClientShip>()
        val calls = mutableListOf<ShipRenderView>()
        val filter = api.registerShipRenderPredicate { candidate, view ->
            assertSame(ship, candidate)
            assertSame(view, api.currentShipRenderView)
            calls.add(view)
            view === camera
        }
        val allow = api.registerShipRenderPredicate { _, _ -> true }
        try {
            ShipRenderViews.render(main, { listOf(ship) }) {
                repeat(3) { assertFalse(ShipRenderViews.shouldRenderShip(ship)) }
                assertThrows(IllegalStateException::class.java) {
                    ShipRenderViews.render(camera, { listOf(ship) }) {
                        assertSame(otherLevel, ShipRenderViews.renderLevel)
                        assertEquals(camera.camera.position, ShipRenderViews.cameraPosition)
                        assertTrue(ShipRenderViews.shouldRenderShip(ship))
                        error("camera render failed")
                    }
                }
                assertSame(mainLevel, ShipRenderViews.renderLevel)
                assertEquals(main.camera.position, ShipRenderViews.cameraPosition)
                assertSame(main, api.currentShipRenderView)
                assertFalse(ShipRenderViews.shouldRenderShip(ship))
            }
            assertEquals(listOf(main, camera), calls)
            assertNull(api.currentShipRenderView)
        } finally {
            allow.unregister()
            filter.unregister()
        }
        ShipRenderViews.render(main, { error("no visibility filters registered") }) {
            assertTrue(ShipRenderViews.shouldRenderShip(ship))
        }
    }
}
