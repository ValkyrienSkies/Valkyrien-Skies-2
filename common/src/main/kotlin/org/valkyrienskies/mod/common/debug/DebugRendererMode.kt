package org.valkyrienskies.mod.common.debug

enum class DebugRendererMode(val commandName: String, val showMarkers: Boolean, val showText: Boolean) {
    NONE("none", false, false),
    MARKERS("markers", true, false),
    TEXT("text", false, true),
    FULL("full", true, true),
}
