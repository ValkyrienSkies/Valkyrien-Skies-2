package org.valkyrienskies.mod.mixin.feature.spawn_player_on_ship;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketChangeKnownShips;
import org.valkyrienskies.mod.mixinducks.feature.tickets.PlayerKnownShipsDuck;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity implements PlayerKnownShipsDuck {

    @Unique
    private LongSet vs_knownShips = new LongOpenHashSet();

    protected MixinPlayer(EntityType<? extends LivingEntity> entityType,
        Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void populateLoadedShips(Level level, BlockPos blockPos, float f, GameProfile gameProfile, CallbackInfo ci) {
        if (level != null && level.isClientSide()) { // Serverside we repopulate it from the previous ServerPlayer in ServerPlayer::restoreFrom.
            VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips().forEach(ship -> vs_knownShips.add(ship.getId()));
        }
    }

    // Per-tick batch of add/remove ship-id notifications pending flush to
    // the server. During a bulk spawn (/vs perf-test spawn-ship-cube 10) we
    // previously sent one PacketChangeKnownShips per ship — 1000 round-trip
    // packets during a single tick. Batching into one packet per tick drops
    // that to 1 roundtrip, saving ~100ms of client-settle on the 1000-ship
    // perf test.
    @Unique
    private final it.unimi.dsi.fastutil.longs.LongArrayList vs_pendingAdds =
        new it.unimi.dsi.fastutil.longs.LongArrayList();
    @Unique
    private final it.unimi.dsi.fastutil.longs.LongArrayList vs_pendingRemoves =
        new it.unimi.dsi.fastutil.longs.LongArrayList();
    @Unique
    private boolean vs_flushScheduled = false;

    @Override
    public void vs_addKnownShip(long shipId) {
        vs_knownShips.add(shipId);
        if (level().isClientSide) {
            vs_pendingAdds.add(shipId);
            vs_scheduleKnownShipsFlush();
        }
    }

    @Override
    public void vs_removeKnownShip(long shipId) {
        vs_knownShips.remove(shipId);
        if (level().isClientSide) {
            vs_pendingRemoves.add(shipId);
            vs_scheduleKnownShipsFlush();
        }
    }

    @Unique
    private void vs_scheduleKnownShipsFlush() {
        vs_flushScheduled = true;
    }

    @Unique
    public void vs_flushKnownShips() {
        if(!vs_flushScheduled) return;
        vs_flushScheduled = false;
        if (!vs_pendingAdds.isEmpty()) {
            long[] ids = vs_pendingAdds.toLongArray();
            vs_pendingAdds.clear();
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking()
                .sendToServer(new PacketChangeKnownShips(true, ids));
        }
        if (!vs_pendingRemoves.isEmpty()) {
            long[] ids = vs_pendingRemoves.toLongArray();
            vs_pendingRemoves.clear();
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking()
                .sendToServer(new PacketChangeKnownShips(false, ids));
        }
    }

    @Override
    public boolean vs_isKnownShip(long shipId) {
        return vs_knownShips.contains(shipId);
    }

    @Override
    public LongSet vs_getKnownShips() {
        return vs_knownShips;
    }

    @Override
    public void vs_setKnownShips(LongSet ships) {
        this.vs_knownShips = ships;
    }
}
