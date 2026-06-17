package org.valkyrienskies.mod.common.air_pockets

// Face-sample conductance uses an 8x8 grid (64 samples).
// Treat <4 samples as a micro-gap, not a real hole/vent/wall opening.
internal const val MIN_OPENING_CONDUCTANCE: Int = 4

