package org.valkyrienskies.mod.common.fluid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult

class FluidSourceWithdrawalHandlerTest {
    @Test
    fun `accepts matching full ship source`() {
        assertNull(
            FluidSourceWithdrawalHandler.validateSource(
                withdrawal(),
                currentOwnerBodyId = SOURCE_BODY_ID,
                currentFluidId = FLUID_ID
            )
        )
    }

    @Test
    fun `rejects a source that moved to another body`() {
        assertEquals(
            VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(
                withdrawal(),
                currentOwnerBodyId = SOURCE_BODY_ID + 1,
                currentFluidId = FLUID_ID
            )
        )
    }

    @Test
    fun `accepts world source only outside ship ownership`() {
        val worldWithdrawal = withdrawal(sourceIsWorldBody = true)
        assertNull(
            FluidSourceWithdrawalHandler.validateSource(
                worldWithdrawal,
                currentOwnerBodyId = null,
                currentFluidId = FLUID_ID
            )
        )
        assertEquals(
            VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(
                worldWithdrawal,
                currentOwnerBodyId = SOURCE_BODY_ID,
                currentFluidId = FLUID_ID
            )
        )
    }

    @Test
    fun `rejects replaced or unsupported fluid sources`() {
        assertEquals(
            VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(
                withdrawal(),
                currentOwnerBodyId = SOURCE_BODY_ID,
                currentFluidId = FLUID_ID + 1
            )
        )
        assertEquals(
            VsiFluidSourceWithdrawalResult.UNSUPPORTED_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(
                withdrawal(fillAmount = 128),
                currentOwnerBodyId = SOURCE_BODY_ID,
                currentFluidId = FLUID_ID
            )
        )
    }

    @Test
    fun `known empty source retains stronger proof for world and ship owners`() {
        for (world in listOf(false, true)) {
            assertEquals(VsiFluidSourceWithdrawalResult.EMPTY_SOURCE,
                FluidSourceWithdrawalHandler.validateSource(withdrawal(sourceIsWorldBody = world),
                    if (world) null else SOURCE_BODY_ID, null, knownEmpty = true))
        }
    }

    @Test
    fun `unknown mapping different fluid and changed owner cannot prove an empty source`() {
        assertEquals(VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(withdrawal(), SOURCE_BODY_ID, null))
        assertEquals(VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(withdrawal(), SOURCE_BODY_ID, FLUID_ID + 1, knownEmpty = true))
        assertEquals(VsiFluidSourceWithdrawalResult.STALE_SOURCE,
            FluidSourceWithdrawalHandler.validateSource(withdrawal(), SOURCE_BODY_ID + 1, null, knownEmpty = true))
        assertNull(FluidSourceWithdrawalHandler.validateSource(withdrawal(), SOURCE_BODY_ID, FLUID_ID, knownEmpty = true))
    }

    private fun withdrawal(
        sourceIsWorldBody: Boolean = false,
        fillAmount: Int = 255
    ) = VsiFluidSourceWithdrawal(
        sequence = 1,
        sourceRevision = 2,
        dimensionId = "minecraft:overworld",
        sourceBodyId = SOURCE_BODY_ID,
        sourceIsWorldBody = sourceIsWorldBody,
        destinationBodyId = 11,
        sourcePositionX = 1,
        sourcePositionY = 2,
        sourcePositionZ = 3,
        fluidId = FLUID_ID,
        fillAmount = fillAmount
    )

    private companion object {
        const val SOURCE_BODY_ID = 7L
        const val FLUID_ID = 3
    }
}
