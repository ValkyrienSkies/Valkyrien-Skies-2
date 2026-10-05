package org.valkyrienskies.mod.api.config

import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VSConfigApiBinaryCompatibilityTest {

    @Test
    fun `legacy forge config builder signature remains available`() {
        val classResource = "org/valkyrienskies/mod/api/config/VSConfigApi.class"
        val classBytes = checkNotNull(javaClass.classLoader.getResourceAsStream(classResource)).use { it.readBytes() }
        val classFile = String(classBytes, StandardCharsets.ISO_8859_1)
        val legacyDescriptor = "(Lorg/valkyrienskies/core/internal/config/VsiConfigModelCategory;" +
            "Lnet/minecraftforge/common/ForgeConfigSpec\$Builder;Lkotlin/jvm/functions/Function2;)" +
            "Lnet/minecraftforge/common/ForgeConfigSpec\$Builder;"

        assertTrue(classFile.contains(legacyDescriptor), "Missing Legacy-compatible buildForgeConfigSpec overload")
    }
}
