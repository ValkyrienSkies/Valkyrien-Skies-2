package org.valkyrienskies.mod.common.fluid

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.util.AerodynamicUtils
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.world.ServerShipWorld

@OptIn(GameTickOnly::class)
class VanillaFluidFlowWindProviderTest {
    @Test
    fun `registration follows world replacement and close without duplicates`() {
        val firstAero = mockk<AerodynamicUtils>(relaxed = true)
        val secondAero = mockk<AerodynamicUtils>(relaxed = true)
        val first = mockk<ServerShipWorld> { every { aerodynamicUtils } returns firstAero }
        val second = mockk<ServerShipWorld> { every { aerodynamicUtils } returns secondAero }
        val provider = VanillaFluidFlowWindProvider
        try {
            provider.ensureRegistered(first)
            provider.ensureRegistered(first)
            provider.ensureRegistered(second)
            provider.onWorldClosed(first)
            verify(exactly = 1) { firstAero.registerWindProvider(provider) }
            verify(exactly = 1) { firstAero.unregisterWindProvider(provider) }
            verify(exactly = 1) { secondAero.registerWindProvider(provider) }
            verify(exactly = 0) { secondAero.unregisterWindProvider(provider) }
            provider.onWorldClosed(second)
            provider.onWorldClosed(second)
            verify(exactly = 1) { secondAero.unregisterWindProvider(provider) }
            provider.ensureRegistered(second)
            verify(exactly = 2) { secondAero.registerWindProvider(provider) }
        } finally {
            provider.onWorldClosed(second)
            provider.onWorldClosed(first)
        }
    }
}
