package org.valkyrienskies.mod.common.util;

import java.util.HashSet;
import java.util.Set;
import org.valkyrienskies.core.internal.world.VsiPlayer;

/**
 * Registry for mock VsiPlayers used in testing.
 * MixinMinecraftServer.preTick includes these in setPlayers() calls each tick.
 * Gametest registers mock players here so vsCore simulates physics.
 */
public final class MockPlayerRegistry {
    private static final Set<VsiPlayer> mockPlayers = new HashSet<>();

    public static void add(VsiPlayer player) {
        mockPlayers.add(player);
    }

    public static void remove(VsiPlayer player) {
        mockPlayers.remove(player);
    }

    public static void clear() {
        mockPlayers.clear();
    }

    public static Set<VsiPlayer> getAll() {
        return mockPlayers;
    }

    private MockPlayerRegistry() {}
}
