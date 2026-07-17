package org.valkyrienskies.mod.common.schematic

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.minecraft.nbt.CompoundTag
import org.joml.Quaterniond
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.internal.joints.VSJoint

/**
 * Pose for a constraint attachment point.
 */
data class VdexJointPose(
    val position: Vector3dc = Vector3d(0.0, 0.0, 0.0),
    val rotation: Quaterniondc = Quaterniond(0.0, 0.0, 0.0, 1.0)
) {

    companion object {
        fun fromPosRot(pos: Vector3d, rot: Quaterniond) = VdexJointPose(
            position = pos,
            rotation = rot
        )
    }
}

/**
 * A constraint between two ships in the schematic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexConstraintMetadata(
    val shipIndex0: Int = 0,
    val shipIndex1: Int = 1,
    val position0offset: Vector3d = Vector3d(0.0, 0.0, 0.0),
    val position1offset: Vector3d = Vector3d(0.0, 0.0, 0.0),
)

/**
 * An individual ship entry in the schematic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexShipEntry(
    val name: String = "",
    val nbtFile: String = "ship_0.nbt",
    val relativePos: Vector3dc = Vector3d(0.0, 0.0, 0.0),
    val relativeRot: Quaterniondc = Quaterniond(0.0, 0.0, 0.0, 1.0),
    val isStatic: Boolean = false,
    val scale: Double = 1.0,
    val attachedData: ByteArray = byteArrayOf()
)

/**
 * An individual constraint entry in the schematic.
 */
data class VdexConstraintEntry(
    val joint: VSJoint,
    val metadata: VdexConstraintMetadata = VdexConstraintMetadata()
)

/**
 * A mod entry representing a mod that was present at schematic creation time. Required means that features of it were used in the saved schematic.
 */
data class VdexModEntry(
    val id: String = "",
    val version: String = "",
    val required: Boolean = false
)

data class VdexSocialMetadata(
    val name: String = "",
    val description: String = "",
    val author: String = ""
)

/**
 * Top-level metadata for a .vdex schematic file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class VdexMetadata(
    val version: Int = 1,
    val social: VdexSocialMetadata = VdexSocialMetadata(),
    val shipIdToIndex: Map<Long, Int> = mapOf(),
    @JsonIgnore val ships: List<VdexShipEntry> = emptyList(),
    @JsonIgnore val constraints: List<VdexConstraintEntry> = emptyList(),
)

/**
 * In-memory representation of a loaded .vdex file.
 */
data class VdexData(
    val metadata: VdexMetadata,
    val modList: List<VdexModEntry>,
    val nbtData: Map<String, CompoundTag>
)
