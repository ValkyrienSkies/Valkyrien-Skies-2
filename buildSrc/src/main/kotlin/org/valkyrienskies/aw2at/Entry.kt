package org.valkyrienskies.aw2at

sealed interface AWEntry
data class ClassEntry(
    val modification: AccessModification,
    val clazz: String
): AWEntry

data class FieldEntry(
    val modification: AccessModification,
    val clazz: String,
    val fieldName: String,
    val fieldType: String
): AWEntry

data class MethodEntry(
    val modification: AccessModification,
    val clazz: String,
    val method: String
): AWEntry

enum class AccessModification {
    ACCESSIBLE,
    MUTABLE
}


