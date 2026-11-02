package org.valkyrienskies.mod.common.render.batched;

import java.util.HashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;

/** Rebuild tinted meshes only when their sampled biome colors change. */
final class ShipBiomeTintCache {
    private record Sample(BlockPos position, ColorResolver resolver) { }
    private final HashMap<Sample, Integer> samples = new HashMap<>();
    private final Vector3d world = new Vector3d();

    void clear() {
        samples.clear();
    }

    int sample(final ClientLevel level, final ClientShip ship, final BlockPos position,
        final ColorResolver resolver) {
        final Sample key = new Sample(position.immutable(), resolver);
        return samples.computeIfAbsent(key, value -> color(level, ship, value));
    }

    boolean hasChanged(final ClientLevel level, final ClientShip ship) {
        for (final var entry : samples.entrySet()) {
            if (color(level, ship, entry.getKey()) != entry.getValue()) return true;
        }
        return false;
    }

    private int color(final ClientLevel level, final ClientShip ship, final Sample sample) {
        final BlockPos position = sample.position();
        ship.getRenderTransform().getShipToWorld().transformPosition(
            world.set(position.getX(), position.getY(), position.getZ()));
        return level.getBlockTint(BlockPos.containing(world.x, world.y, world.z), sample.resolver());
    }
}
