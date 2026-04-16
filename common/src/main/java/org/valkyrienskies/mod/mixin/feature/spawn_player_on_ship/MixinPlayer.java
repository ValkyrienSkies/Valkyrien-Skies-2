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

    @Override
    public void vs_addKnownShip(long shipId) {
        vs_knownShips.add(shipId);
        if (level().isClientSide) {
            var packet = new PacketChangeKnownShips(true, shipId);
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
        }
    }

    @Override
    public void vs_removeKnownShip(long shipId) {
        vs_knownShips.remove(shipId);
        if (level().isClientSide) {
            var packet = new PacketChangeKnownShips(false, shipId);
            ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
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
