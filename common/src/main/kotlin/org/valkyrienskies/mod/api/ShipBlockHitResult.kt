package org.valkyrienskies.mod.api

import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Valkyrien Skies modifies vanilla methods to return an instance of [ShipBlockHitResult] when a raycast hits a block
 * on a ship.
 *
 * By default, we set the [location] to be in world-space, while the [blockPos] is in ship-space. However,
 * in some cases, mods want the [location] to be in ship-space as well. In that case, that mod (or a mixin into it)
 * can check if a hit result is `instanceof ShipBlockHitResult` and use either [useLocationInShip] or
 * [withLocationInShip] to change the [location].
 *
 * See also: [#613](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/613)
 *
 * @constructor The default constructor uses the [locationInWorld] to set [location]. The [location] of the original
 * [BlockHitResult] is ignored.
 */
class ShipBlockHitResult private constructor(
    hitResult: BlockHitResult,
    /**
     * The location this raycast hit, in ship coordinates
     */
    val locationInShip: Vec3,
    /**
     * The location this raycast hit, in world coordinates
     */
    val locationInWorld: Vec3,
) : BlockHitResult(locationInWorld, hitResult.direction, hitResult.blockPos, hitResult.isInside) {

    companion object {
        @JvmStatic
        fun create(hitResult: BlockHitResult, locationInShip: Vec3, locationInWorld: Vec3) =
            ShipBlockHitResult(hitResult, locationInShip, locationInWorld)
    }

    init {
        require(hitResult.type != Type.MISS) { "Cannot construct a ShipBlockHitResult out of a miss." }
    }

    /**
     * Sets [location] to [locationInShip]. This *mutates* the current hit result - use carefully.
     */
    fun useLocationInShip(): BlockHitResult = also {
        this.location = locationInShip
    }

    /**
     * Sets [location] to [locationInWorld]. This *mutates* the current hit result - use carefully.
     */
    fun useLocationInWorld(): BlockHitResult = also {
        this.location = locationInWorld
    }

    /**
     * Returns a new copy of this [ShipBlockHitResult] with [location] set to [locationInWorld].
     */
    fun withLocationInWorld(): ShipBlockHitResult =
        ShipBlockHitResult(this, locationInShip, locationInWorld)
            .also { it.useLocationInWorld() }

    /**
     * Returns a new copy of this [ShipBlockHitResult] with [location] set to [locationInShip].
     */
    fun withLocationInShip(): ShipBlockHitResult =
        ShipBlockHitResult(this, locationInShip, locationInWorld)
            .also { it.useLocationInShip() }
}
