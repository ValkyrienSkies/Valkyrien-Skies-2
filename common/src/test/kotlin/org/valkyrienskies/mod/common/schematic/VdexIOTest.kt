package org.valkyrienskies.mod.common.schematic

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VdexIOTest {
    // Disabled because we disabled the custom VDex header for now
    /*private val expectedHeader = byteArrayOf(0x56, 0x44, 0x45, 0x58, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun `write prefixes file with vdex header`() {
        val path = Files.createTempFile("schematic", ".vdex")
        try {
            val metadata = VdexMetadata(version = 2)

            VdexIO.write(path, metadata, emptyMap())

            val bytes = Files.readAllBytes(path)
            assertArrayEquals(expectedHeader, bytes.copyOfRange(0, expectedHeader.size))
            assertEquals(metadata, VdexIO.read(path).metadata)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `read rejects zip file without vdex header`() {
        val path = Files.createTempFile("renamed-zip", ".vdex")
        try {
            ZipOutputStream(Files.newOutputStream(path).buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("metadata.json"))
                zip.write("""{"version":1,"ships":[]}""".toByteArray())
                zip.closeEntry()
            }

            val exception = assertThrows(IllegalArgumentException::class.java) {
                VdexIO.read(path)
            }
            assertEquals("Invalid .vdex file header", exception.message)
        } finally {
            Files.deleteIfExists(path)
        }
    }*/
}
