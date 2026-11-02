package org.valkyrienskies.mod.common.fluid

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldFluidOutflowManagerTest {
    @Test
    fun `falls through empty space`() {
        assertEquals(
            FluidParcelAction.FALL,
            chooseFluidParcelAction(
                currentReplaceable = true,
                currentIsSameSource = false,
                belowReplaceable = true,
                belowIsSameSource = false,
            )
        )
    }

    @Test
    fun `settles above a solid block`() {
        assertEquals(
            FluidParcelAction.SETTLE,
            chooseFluidParcelAction(
                currentReplaceable = true,
                currentIsSameSource = false,
                belowReplaceable = false,
                belowIsSameSource = false,
            )
        )
    }

    @Test
    fun `merges into an existing source`() {
        assertEquals(
            FluidParcelAction.MERGE,
            chooseFluidParcelAction(
                currentReplaceable = true,
                currentIsSameSource = false,
                belowReplaceable = false,
                belowIsSameSource = true,
            )
        )
    }

    @Test
    fun `does not replace an outlet that became blocked`() {
        assertEquals(
            FluidParcelAction.WAIT,
            chooseFluidParcelAction(
                currentReplaceable = false,
                currentIsSameSource = false,
                belowReplaceable = true,
                belowIsSameSource = false,
            )
        )
    }

    @Test
    fun `airborne visuals remain visible for sub-layer droplets`() {
        assertEquals(1, worldVolumeToVisualLevel(1 / 255.0))
        assertEquals(4, worldVolumeToVisualLevel(127 / 255.0))
        assertEquals(8, worldVolumeToVisualLevel(1.0))
        assertEquals(1, worldVolumeToVisualLevel(0.125))
        assertEquals(6, worldVolumeToVisualLevel(0.75))
        assertEquals(8, worldVolumeToVisualLevel(1e20))
    }

    @Test
    fun `landed fluid levels round down at exact decimal boundaries`() {
        val unit = java.math.BigDecimal("31.875")
        val epsilon = java.math.BigDecimal("0.00000000000000000000001")
        assertEquals(0, settledFluidLevel(java.math.BigDecimal.ZERO))
        for (level in 1..8) {
            val amount = unit * level.toBigDecimal()
            assertEquals(level - 1, settledFluidLevel(amount - epsilon))
            assertEquals(level, settledFluidLevel(amount))
            assertEquals(level, settledFluidLevel(amount + epsilon))
        }
    }

    @Test
    fun `ballistic parcel keeps horizontal launch velocity and gains gravity`() {
        val velocity = Vector3d(20.0, 0.0, 0.0)
        val displacement = fluidParcelTickDisplacement(velocity)

        assertEquals(1.0, displacement.x, 1.0e-9)
        assertEquals(-0.024525, displacement.y, 1.0e-9)
        assertEquals(-0.4905, velocity.y, 1.0e-9)
        assertTrue(fluidParcelMotionSubsteps(displacement) >= 3)
    }

    @Test
    fun `motion substeps remain bounded`() {
        assertEquals(1, fluidParcelMotionSubsteps(Vector3d()))
        assertEquals(32, fluidParcelMotionSubsteps(Vector3d(100.0, 0.0, 0.0)))
    }
}
