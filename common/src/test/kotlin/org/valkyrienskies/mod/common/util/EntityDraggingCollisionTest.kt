package org.valkyrienskies.mod.common.util

import io.mockk.every
import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import org.joml.Vector3d
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class EntityDraggingCollisionTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @Test fun `drag sweep stops at world walls while preserving movement along the wall`() {
        val level = mockk<Level>()
        every { level.worldBorder } returns WorldBorder()
        val floor = Shapes.create(AABB(-10.0, 0.0, -10.0, 10.0, 1.0, 10.0))
        val wall = Shapes.create(AABB(1.0, 1.0, -10.0, 2.0, 10.0, 10.0))
        every { level.getBlockCollisions(null, any()) } returns listOf(floor, wall)
        val box = AABB(0.2, 1.0, 0.2, 0.8, 2.8, 0.8)
        val result = MovementSubsteps.resolve(Vector3d(2.0, -0.08, 2.0)) { step, offset ->
            Entity.collideBoundingBox(null, step.toMinecraft(), box.move(offset.toMinecraft()), level, emptyList()).toJOML()
        }
        assertEquals(0.2, result.x, 1e-10)
        assertEquals(0.0, result.y, 1e-10)
        assertEquals(2.0, result.z, 1e-10)
    }

    @Test fun `upward dragging cannot carry the entity through a world ceiling`() {
        val level = mockk<Level>()
        every { level.worldBorder } returns WorldBorder()
        val ceiling = Shapes.create(AABB(-10.0, 3.0, -10.0, 10.0, 4.0, 10.0))
        every { level.getBlockCollisions(null, any()) } returns listOf(ceiling)
        val box = AABB(0.2, 1.0, 0.2, 0.8, 2.8, 0.8)
        val result = MovementSubsteps.resolve(Vector3d(0.0, 2.0, 0.0)) { step, offset ->
            Entity.collideBoundingBox(null, step.toMinecraft(), box.move(offset.toMinecraft()), level, emptyList()).toJOML()
        }
        assertEquals(0.2, result.y, 1e-10)
    }
}
