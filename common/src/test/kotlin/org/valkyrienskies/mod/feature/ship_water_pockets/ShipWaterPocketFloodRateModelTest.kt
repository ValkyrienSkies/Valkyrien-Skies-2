package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.mockk
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.air_pockets.computeFloodProgressRateModel

class ShipWaterPocketFloodRateModelTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    private val level: Level = mockk(relaxed = true)

    @Test
    fun largerOpeningConductanceAdvancesMorePerFluidCadence() {
        val narrow = computeFloodProgressRateModel(
            level = level,
            floodFluid = Fluids.WATER,
            openingConductanceUnits = 1,
            openingCount = 1,
        )
        val wide = computeFloodProgressRateModel(
            level = level,
            floodFluid = Fluids.WATER,
            openingConductanceUnits = 6,
            openingCount = 3,
        )

        assertTrue(wide.frontierBudget > narrow.frontierBudget)
        assertTrue(wide.planeDeltaPerTick > narrow.planeDeltaPerTick)
    }

    @Test
    fun slowerFluidsAdvanceMoreSlowlyThanWater() {
        val water = computeFloodProgressRateModel(
            level = level,
            floodFluid = Fluids.WATER,
            openingConductanceUnits = 4,
            openingCount = 2,
        )
        val lava = computeFloodProgressRateModel(
            level = level,
            floodFluid = Fluids.LAVA,
            openingConductanceUnits = 4,
            openingCount = 2,
        )

        assertTrue(lava.fluidTickDelay > water.fluidTickDelay)
        assertTrue(lava.planeDeltaPerTick < water.planeDeltaPerTick)
    }
}
