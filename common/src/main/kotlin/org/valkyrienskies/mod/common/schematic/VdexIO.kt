package org.valkyrienskies.mod.common.schematic

import com.fasterxml.jackson.core.type.TypeReference
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import org.valkyrienskies.mod.common.vsCore
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.Arrays
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object VdexIO {
    private val mapper = vsCore.stringMapper
    private val fileHeader = byteArrayOf(0x56, 0x44, 0x45, 0x58, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Write a .vdex file (magic header + ZIP containing metadata.json + .nbt files).
     */
    fun write(path: Path, metadata: VdexMetadata, modList: List<VdexModEntry> = emptyList(), nbtData: Map<String, CompoundTag>) {
        ZipOutputStream(path.toFile().outputStream().buffered()).use { zip ->
            //output.write(fileHeader)

            val ships = metadata.ships
            val joints = metadata.constraints

            // Write metadata.json
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata))
            zip.closeEntry()

            // Write modlist.json
            zip.putNextEntry(ZipEntry("modlist.json"))
            zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(modList))
            zip.closeEntry()

            // Write each ship.json
            zip.putNextEntry(ZipEntry("ships/"))
            zip.closeEntry()
            val usedNames = mutableSetOf<String>()
            for (ship in ships) {
                val name = if (usedNames.contains(ship.name)) {
                    "${ship.name}_${usedNames.count { it.startsWith(ship.name) }}"
                } else {
                    ship.name
                }
                usedNames.add(name)
                zip.putNextEntry(ZipEntry("ships/${name}.json"))
                zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(ship))
                zip.closeEntry()
            }

            // Write each joint.json
            zip.putNextEntry(ZipEntry("constraints/"))
            zip.closeEntry()
            val usedJointNames = mutableSetOf<String>()
            var jointIndex = 0
            for (jointPair in joints) {
                // joint type + ships between + index
                var jointIdentifier = jointPair.joint.jointType.name + "_" + jointPair.metadata.shipIndex0 + "-" + jointPair.metadata.shipIndex1
                if (usedJointNames.contains(jointIdentifier)) {
                    jointIdentifier += "_${usedJointNames.count { it.startsWith(jointIdentifier) }}"
                }
                usedJointNames.add(jointIdentifier)
                zip.putNextEntry(ZipEntry("constraints/${jointIdentifier}.json"))
                zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(jointPair))
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("structure/"))
            zip.closeEntry()
            // Write each .nbt file
            for ((name, tag) in nbtData) {
                zip.putNextEntry(ZipEntry("structure/$name"))
                val dataOut = DataOutputStream(zip)
                NbtIo.write(tag, dataOut)
                zip.closeEntry()
            }
        }
    }

    /**
     * Read a .vdex file back into memory.
     */
    fun read(path: Path): VdexData {
        val nbtData = linkedMapOf<String, CompoundTag>()
        val ships = mutableListOf<VdexShipEntry>()
        val constraints = mutableListOf<VdexConstraintEntry>()

        var metadata: VdexMetadata? = null
        var modList: List<VdexModEntry> = emptyList()

        val modListType = object : TypeReference<List<VdexModEntry>>() {}

        path.toFile().inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    try {
                        if (entry.isDirectory) {
                            continue
                        }

                        when {
                            entry.name == "metadata.json" -> {
                                metadata = mapper.readValue(zip.readBytes(), VdexMetadata::class.java)
                            }

                            // Your writer currently uses "modlist.json".
                            // This also accepts "modList.json" in case older/test files used that spelling.
                            entry.name == "modlist.json" || entry.name == "modList.json" -> {
                                modList = mapper.readValue(zip.readBytes(), modListType)
                            }

                            entry.name.startsWith("ships/") && entry.name.endsWith(".json") -> {
                                ships += mapper.readValue(zip.readBytes(), VdexShipEntry::class.java)
                            }

                            entry.name.startsWith("constraints/") && entry.name.endsWith(".json") -> {
                                constraints += mapper.readValue(zip.readBytes(), VdexConstraintEntry::class.java)
                            }

                            entry.name.startsWith("structure/") -> {
                                val structureName = entry.name.removePrefix("structure/")

                                if (structureName.isNotBlank()) {
                                    val tag = NbtIo.read(DataInputStream(zip))
                                    nbtData[structureName] = tag
                                }
                            }
                        }
                    } finally {
                        zip.closeEntry()
                    }
                }
            }
        }

        val baseMetadata = metadata
            ?: throw IllegalStateException("No metadata.json found in .vdex file")

        return VdexData(
            metadata = baseMetadata.copy(
                ships = ships,
                constraints = constraints
            ),
            modList = modList,
            nbtData = nbtData
        )
    }

    private fun requireVdexHeader(input: InputStream) {
        val header = input.readNBytes(fileHeader.size)
        if (!Arrays.equals(header, fileHeader)) {
            throw IllegalArgumentException("Invalid .vdex file header")
        }
    }
}
