package org.valkyrienskies.mod.common.schematic

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import org.joml.Quaterniond
import org.joml.Vector3d
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pose for a constraint attachment point.
 */
data class VdexJointPose(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val rotX: Double = 0.0,
    val rotY: Double = 0.0,
    val rotZ: Double = 0.0,
    val rotW: Double = 1.0
) {
    fun toPosition() = Vector3d(x, y, z)
    fun toRotation() = Quaterniond(rotX, rotY, rotZ, rotW)

    companion object {
        fun fromPosRot(pos: Vector3d, rot: Quaterniond) = VdexJointPose(
            pos.x, pos.y, pos.z,
            rot.x, rot.y, rot.z, rot.w
        )
    }
}

/**
 * A constraint between two ships in the schematic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexConstraintEntry(
    val type: String = "revolute",
    val shipIndex0: Int = 0,
    val shipIndex1: Int = 1,
    val pose0: VdexJointPose = VdexJointPose(),
    val pose1: VdexJointPose = VdexJointPose(),
    val maxForceTorque: Double? = null,
    val driveFreeSpin: Boolean = true
)

/**
 * An individual ship entry in the schematic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexShipEntry(
    val name: String = "",
    val nbtFile: String = "ship_0.nbt",
    val relativeX: Double = 0.0,
    val relativeY: Double = 0.0,
    val relativeZ: Double = 0.0,
    val isStatic: Boolean = false,
    val scale: Double = 1.0
)

/**
 * Top-level metadata for a .vdex schematic file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexMetadata(
    val version: Int = 1,
    val mainShipIndex: Int = 0,
    val ships: List<VdexShipEntry> = emptyList(),
    val constraints: List<VdexConstraintEntry> = emptyList()
)

/**
 * In-memory representation of a loaded .vdex file.
 */
data class VdexData(
    val metadata: VdexMetadata,
    val nbtData: Map<String, CompoundTag>
)

object VdexIO {
    private val mapper = ObjectMapper()

    /**
     * Write a .vdex file (ZIP containing metadata.json + .nbt files).
     */
    fun write(path: Path, metadata: VdexMetadata, nbtData: Map<String, CompoundTag>) {
        ZipOutputStream(path.toFile().outputStream().buffered()).use { zip ->
            // Write metadata.json
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata))
            zip.closeEntry()

            // Write each .nbt file
            for ((name, tag) in nbtData) {
                zip.putNextEntry(ZipEntry(name))
                val baos = ByteArrayOutputStream()
                NbtIo.write(tag, DataOutputStream(baos))
                zip.write(baos.toByteArray())
                zip.closeEntry()
            }
        }
    }

    /**
     * Read a .vdex file back into memory.
     */
    fun read(path: Path): VdexData {
        val nbtData = mutableMapOf<String, CompoundTag>()
        var metadata: VdexMetadata? = null

        ZipInputStream(path.toFile().inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val bytes = zip.readBytes()
                if (entry.name == "metadata.json") {
                    metadata = mapper.readValue(bytes, VdexMetadata::class.java)
                } else if (entry.name.endsWith(".nbt")) {
                    val tag = NbtIo.read(DataInputStream(ByteArrayInputStream(bytes)))
                    nbtData[entry.name] = tag
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return VdexData(
            metadata ?: throw IllegalStateException("No metadata.json found in .vdex file"),
            nbtData
        )
    }
}
