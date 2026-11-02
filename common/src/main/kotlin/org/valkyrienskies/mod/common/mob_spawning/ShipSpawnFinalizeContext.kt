package org.valkyrienskies.mod.common.mob_spawning

import org.valkyrienskies.core.api.ships.Ship

/**
 * Thread-local stack of the ship whose mob is currently being finalized, pushed around the
 * finalize call by the per-spawn-path mixins and read by `MixinBiomeManagerShipFinalize` to
 * project `getBiome` to the ship's rendered position. Stacked for nesting safety.
 *
 * On Fabric the mixins wrap the `Mob.finalizeSpawn` invoke; on Forge that invoke is
 * binpatched to `ForgeEventFactory.onFinalizeSpawn` and the forge-module mixins wrap that
 * instead (except BaseSpawner, which keeps the vanilla call on both loaders).
 */
object ShipSpawnFinalizeContext {

    private val STACK: ThreadLocal<ArrayDeque<Ship?>> = ThreadLocal.withInitial { ArrayDeque() }

    @JvmStatic
    fun push(ship: Ship?) {
        STACK.get().addLast(ship)
    }

    @JvmStatic
    fun pop() {
        STACK.get().removeLast()
    }

    @JvmStatic
    fun current(): Ship? = STACK.get().lastOrNull()
}
