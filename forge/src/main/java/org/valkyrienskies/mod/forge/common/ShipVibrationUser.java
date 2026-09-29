package org.valkyrienskies.mod.forge.common;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * A {@link VibrationSystem.User} on a ship, seen from world space for one {@link VibrationSystem.Ticker#tick} call.
 *
 * <p>The ticker computes the distance a vibration travelled and the particle path from the user's position source.
 * For a user on a ship that source is in shipyard coordinates while the vibration itself is stored in world
 * coordinates (see {@code MixinVibrationSystem}), so the ticker has to see the source in world space too. Forge's
 * Mixin cannot inject into the ticker interface ({@code MixinVibrationSystemTicker} only applies with Fabric's Mixin
 * or MixinBooster), so the Forge mixins wrap the user at the call sites of the ticker instead.
 */
public final class ShipVibrationUser implements VibrationSystem.User {
    private final VibrationSystem.User user;
    private final Level level;

    private ShipVibrationUser(final VibrationSystem.User user, final Level level) {
        this.user = user;
        this.level = level;
    }

    /**
     * @return {@code user} itself unless {@code pos}, the position of the user, is managed by a ship
     */
    public static VibrationSystem.User wrap(final Level level, final BlockPos pos, final VibrationSystem.User user) {
        if (VSGameUtilsKt.getShipManagingPos(level, pos) == null) {
            return user;
        }
        return new ShipVibrationUser(user, level);
    }

    @Override
    public PositionSource getPositionSource() {
        final PositionSource source = user.getPositionSource();
        final Optional<Vec3> pos = source.getPosition(level);
        if (pos.isEmpty() || VSGameUtilsKt.getShipManagingPos(level, pos.get()) == null) {
            return source;
        }
        /* Curiously, Minecraft doesn't have a decent PositionSource for Vec3 coordinates. While using BlockPos will
        cause precision loss up to half a block for each coordinate, it is far safer than implementing a custom
        PositionSource just for this specific case. */
        return new BlockPositionSource(BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(level, pos.get())));
    }

    @Override
    public int getListenerRadius() {
        return user.getListenerRadius();
    }

    @Override
    public boolean canReceiveVibration(final ServerLevel level, final BlockPos pos, final GameEvent event,
        final GameEvent.Context context) {
        return user.canReceiveVibration(level, pos, event, context);
    }

    @Override
    public void onReceiveVibration(final ServerLevel level, final BlockPos pos, final GameEvent event,
        @Nullable final Entity entity, @Nullable final Entity projectileOwner, final float distance) {
        user.onReceiveVibration(level, pos, event, entity, projectileOwner, distance);
    }

    @Override
    public TagKey<GameEvent> getListenableEvents() {
        return user.getListenableEvents();
    }

    @Override
    public boolean canTriggerAvoidVibration() {
        return user.canTriggerAvoidVibration();
    }

    @Override
    public boolean requiresAdjacentChunksToBeTicking() {
        return user.requiresAdjacentChunksToBeTicking();
    }

    @Override
    public int calculateTravelTimeInTicks(final float distance) {
        return user.calculateTravelTimeInTicks(distance);
    }

    @Override
    public boolean isValidVibration(final GameEvent event, final GameEvent.Context context) {
        return user.isValidVibration(event, context);
    }

    @Override
    public void onDataChanged() {
        user.onDataChanged();
    }
}
