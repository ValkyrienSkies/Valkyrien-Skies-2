package org.valkyrienskies.mod.air_pockets.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.exp

class ShipInteriorFogRendererTest {
    // @Test
    // fun `interior fog only renders when air pocket also suppresses world fluid`() {
    //     assertTrue(ShipInteriorFogRenderer.shouldRenderInteriorWaterFog(true, true))
    //     assertFalse(ShipInteriorFogRenderer.shouldRenderInteriorWaterFog(true, false))
    //     assertFalse(ShipInteriorFogRenderer.shouldRenderInteriorWaterFog(false, true))
    //     assertFalse(ShipInteriorFogRenderer.shouldRenderInteriorWaterFog(false, false))
    // }
    //
    // @Test
    // fun `exp fog formula stays zero before start and increases after start`() {
    //     val density = 0.045f
    //     val fogStart = 2.0f
    //
    //     fun fogAmount(distance: Float): Float {
    //         val fogDistance = maxOf(0.0f, distance - fogStart)
    //         return (1.0 - exp((-fogDistance * density).toDouble())).toFloat()
    //     }
    //
    //     assertEquals(0.0f, fogAmount(0.5f))
    //     assertEquals(0.0f, fogAmount(2.0f))
    //     assertTrue(fogAmount(6.0f) > 0.0f)
    //     assertTrue(fogAmount(20.0f) > fogAmount(6.0f))
    // }
}
