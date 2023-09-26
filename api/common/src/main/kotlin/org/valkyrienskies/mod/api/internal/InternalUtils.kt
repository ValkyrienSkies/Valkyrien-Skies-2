@file:Internal
package org.valkyrienskies.mod.api.internal

import org.jetbrains.annotations.ApiStatus.Internal
import org.joml.primitives.AABBic

// todo remove this

@JvmSynthetic
internal fun require4bits(name: String, aabb: AABBic) {
    require4bits("$name.minX", aabb.minX())
    require4bits("$name.minY", aabb.minY())
    require4bits("$name.minZ", aabb.minZ())
    require4bits("$name.maxX", aabb.maxX())
    require4bits("$name.maxY", aabb.maxY())
    require4bits("$name.maxZ", aabb.maxZ())
}

@JvmSynthetic
internal fun require4bits(name: String, value: Int) {
    require(value in 0..15) { "$name must be >= 0 and <= 15, but is $value" }
}
