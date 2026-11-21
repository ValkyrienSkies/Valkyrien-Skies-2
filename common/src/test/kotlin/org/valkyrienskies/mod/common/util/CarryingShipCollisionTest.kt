package org.valkyrienskies.mod.common.util

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Matrix4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.VsiCoreFactory
import org.valkyrienskies.core.internal.world.VsiShipWorld
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

class CarryingShipCollisionTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
        private val collider by lazy { VsiCoreFactory.instance.newVsCoreServer(mockk(relaxed = true)).entityPolygonCollider }
    }

    private val level = mockk<Level>()
    private val world = mockk<VsiShipWorld>(relaxed = true)
    private val core = mockk<VsiCore>()
    private val ship = mockk<LoadedShip>(relaxed = true)
    private val entity = mockk<Entity>(relaxed = true, moreInterfaces = arrayOf(IEntityDraggingInformationProvider::class))
    private val box = AABB(0.2, 1.0, 0.2, 0.8, 2.8, 0.8)
    private val blocks = mutableListOf<AABB>()
    private val terrain = mutableListOf<AABB>()
    private var transform = Matrix4d()

    @BeforeEach fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { vsCore } returns core
        every { core.entityPolygonCollider } returns collider
        every { level.shipObjectWorld } returns world
        every { level.dimensionId } returns "test"
        every { entity.level() } returns level
        every { entity.boundingBox } returns box
        every { level.worldBorder } returns WorldBorder()
        every { level.getEntityCollisions(entity, any()) } returns emptyList()
        every { ship.id } returns 1L
        every { ship.shipAABB } returns org.joml.primitives.AABBi(990, -10, -10, 1010, 10, 10)
        every { ship.activeChunksSet.contains(any(), any()) } returns true
        every { ship.transform.shipToWorld } answers { Matrix4d(transform).translate(-1000.0, 0.0, 0.0) }
        every { ship.transform.worldToShip } answers { ship.transform.shipToWorld.invert(Matrix4d()) }
        every { world.loadedShips.isEmpty() } answers { blocks.isEmpty() }
        every { world.loadedShips.getIntersecting(any(), any()) } returns listOf(ship)
        every { level.getBlockCollisions(entity, any()) } answers {
            val query = secondArg<AABB>()
            val candidates = if (query.minX > 900.0) blocks.map { it.move(1000.0, 0.0, 0.0) } else terrain
            candidates.filter { it.intersects(query.inflate(1e-7)) }.map(Shapes::create)
        }
    }

    @AfterEach fun cleanup() = unmockkAll()

    @Test fun `rolling ship resolves its wall and deck against the upright entity`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        blocks += AABB(1.0, 1.0, -4.0, 2.0, 4.0, 4.0)
        transform.rotateZ(Math.toRadians(30.0))
        val result = drag()
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `shrinking ship ceiling cannot remain inside the entity head`() {
        blocks += AABB(-4.0, 3.0, -4.0, 4.0, 4.0, 4.0)
        transform.scale(0.7)
        val result = drag()
        assertEquals(0.3, box.minY + result.y(), 2e-7)
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `opposite roll resolves a wall penetrating farther than the entity width`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        blocks += AABB(-1.0, 1.0, -4.0, 0.0, 4.0, 4.0)
        transform.rotateZ(Math.toRadians(-30.0))
        assertNoShipOverlap(box.move(drag().toMinecraft()))
    }

    @Test fun `pure translation carries the entity through the old position of its own wall`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        blocks += AABB(-1.0, 1.0, -4.0, 0.0, 4.0, 4.0)
        transform.translate(5.0, 3.0, 0.0)
        val result = drag()
        assertTrue(result.equals(Vector3d(5.0, 3.0, 0.0), 1e-7))
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `stationary tilted deck resolves overlap even with zero positional drag`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        transform.translate(0.5, 1.0, 0.5).rotateZ(Math.toRadians(30.0)).translate(-0.5, -1.0, -0.5)
        val result = drag()
        assertEquals(0.3 * Math.tan(Math.toRadians(30.0)), result.y(), 2e-7)
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `yawing ship wall cannot overlap a corner of the entity`() {
        blocks += AABB(0.85, 1.0, -4.0, 1.85, 4.0, 4.0)
        transform.translate(0.5, 1.0, 0.5).rotateY(Math.toRadians(45.0)).translate(-0.5, -1.0, -0.5)
        val result = drag()
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `carrier correction slides along a world wall without leaving either overlap`() {
        blocks += AABB(0.85, 1.0, -4.0, 1.85, 4.0, 4.0)
        transform.translate(0.5, 1.0, 0.5).rotateY(Math.toRadians(45.0)).translate(-0.5, -1.0, -0.5)
        terrain += AABB(-4.0, 0.0, 0.83, 4.0, 4.0, 1.83)
        val result = drag()
        val resolved = box.move(result.toMinecraft())
        assertTrue(resolved.maxZ <= 0.83 + 1e-7)
        assertNoShipOverlap(resolved)
    }

    @Test fun `support correction on a descending tilted deck is not world landing`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        transform.translate(0.0, -0.5, 0.0).rotateZ(Math.toRadians(30.0))
        val movement = ShipDraggingMath.movement(Vector3d(0.5, 1.0, 0.5), Matrix4d(), transform)
        val result = EntityShipCollisionUtils.adjustShipDraggingMovement(entity, 1L, movement)
        assertTrue(result.movement.y() > movement.y())
        assertFalse(result.landedOnWorld)
        assertNoShipOverlap(box.move(result.movement.toMinecraft()))
    }

    @Test fun `landing on another ship does not count as world landing`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        every { ship.id } returns 2L
        val result = EntityShipCollisionUtils.adjustShipDraggingMovement(entity, 1L, Vector3d(1.0, -0.08, 0.0))
        assertEquals(1.0, result.movement.x(), 1e-7)
        assertEquals(0.0, result.movement.y(), 1e-7)
        assertFalse(result.landedOnWorld)
    }

    @Test fun `world floor contact is reported independently of ship collisions`() {
        terrain += AABB(-4.0, -1.0, -4.0, 4.0, 0.7, 4.0)
        val result = EntityShipCollisionUtils.adjustShipDraggingMovement(entity, 1L, Vector3d(1.0, -2.0, 0.0))
        assertEquals(1.0, result.movement.x(), 1e-7)
        assertEquals(-0.3, result.movement.y(), 1e-7)
        assertTrue(result.landedOnWorld)
    }

    @Test fun `dragging clipped by terrain remains outside the world wall`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        terrain += AABB(1.0, 1.0, -4.0, 2.0, 4.0, 4.0)
        transform.translate(5.0, 0.0, 2.0)
        val result = drag()
        assertEquals(0.2, result.x(), 1e-7)
        assertEquals(2.0, result.z(), 1e-7)
        assertNoShipOverlap(box.move(result.toMinecraft()))
    }

    @Test fun `resolved contact on a tilted ship remains stationary on subsequent ticks`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        blocks += AABB(1.0, 1.0, -4.0, 2.0, 4.0, 4.0)
        transform.rotateZ(Math.toRadians(30.0))
        val resolved = box.move(drag().toMinecraft())
        every { entity.boundingBox } returns resolved
        repeat(20) {
            val result = EntityShipCollisionUtils.adjustShipDraggingMovement(entity, 1L, Vector3d())
            assertTrue(result.movement.lengthSquared() < 1e-14)
            assertFalse(result.landedOnWorld)
        }
    }

    @Test fun `drag tick retains attachment when a descending ship lifts the supporting corner`() {
        blocks += AABB(-4.0, 0.0, -4.0, 4.0, 1.0, 4.0)
        transform.translate(0.0, -0.5, 0.0).rotateZ(Math.toRadians(30.0))
        val info = dragTick()
        assertEquals(1L, info.lastShipStoodOn)
        assertNoShipOverlap(box.move(info.addedMovementLastTick.toMinecraft()))
        verify { entity.setPos(0.5 + info.addedMovementLastTick.x(), 1.0 + info.addedMovementLastTick.y(), 0.5) }
    }

    @Test fun `drag tick detaches only after landing on the world floor`() {
        terrain += AABB(-4.0, -1.0, -4.0, 4.0, 0.7, 4.0)
        transform.translate(0.0, -2.0, 0.0)
        val info = dragTick()
        assertNull(info.lastShipStoodOn)
        verify { entity.setPos(0.5, match { kotlin.math.abs(it - 0.7) < 1e-7 }, 0.5) }
    }

    private fun dragTick(): EntityDraggingInformation {
        val info = EntityDraggingInformation().apply { lastShipStoodOn = 1L }
        every { (entity as IEntityDraggingInformationProvider).draggingInformation } returns info
        every { entity.x } returns 0.5
        every { entity.y } returns 1.0
        every { entity.z } returns 0.5
        every { entity.vehicle } returns null
        every { world.allShips.getById(1L) } returns ship
        every { ship.prevTickTransform.worldToShip } returns Matrix4d().translation(1000.0, 0.0, 0.0)
        every { ship.prevTickTransform.shipToWorldRotation } returns Quaterniond()
        every { ship.transform.shipToWorldRotation } returns transform.getNormalizedRotation(Quaterniond())
        mockkStatic(EntityDragger::class, EntityShipCollisionUtils::class)
        every { EntityDragger.isDraggable(entity) } returns true
        every { EntityShipCollisionUtils.isCollidingWithUnloadedShips(entity) } returns false
        EntityDragger.dragEntitiesWithShips(listOf(entity))
        return info
    }

    private fun drag(): Vector3dc {
        val movement = ShipDraggingMath.movement(Vector3d(0.5, 1.0, 0.5), Matrix4d(), transform)
        return EntityShipCollisionUtils.adjustShipDraggingMovement(entity, 1L, movement).movement
    }

    private fun assertNoShipOverlap(bounds: AABB) {
        val unitAxes = listOf(Vector3d(1.0, 0.0, 0.0), Vector3d(0.0, 1.0, 0.0), Vector3d(0.0, 0.0, 1.0))
        val entityPoints = collider.createPolygonFromAABB(bounds.toJOML()).points.toList()
        for (block in blocks) {
            val polygon = collider.createPolygonFromAABB(block.toJOML(), transform)
            val axes = unitAxes + polygon.normals + polygon.normals.flatMap { normal ->
                unitAxes.map { normal.cross(it, Vector3d()) }.filter { it.lengthSquared() > 1e-12 }
            }
            val separated = axes.any { normal ->
                val entityProjection = entityPoints.map { it.dot(normal) }
                val blockProjection = polygon.points.map { it.dot(normal) }
                entityProjection.maxOrNull()!! <= blockProjection.minOrNull()!! + 1e-7 ||
                    blockProjection.maxOrNull()!! <= entityProjection.minOrNull()!! + 1e-7
            }
            assertTrue(separated, "Entity $bounds overlaps ship block $block")
        }
    }
}
