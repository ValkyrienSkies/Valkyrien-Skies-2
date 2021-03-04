package org.valkyrienskies.mod.mixin.server;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {

    @Shadow
    @Final
    private List<ServerPlayerEntity> players;

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    public abstract void sendToAround(@Nullable PlayerEntity player, double x, double y, double z, double distance,
        RegistryKey<World> worldKey, Packet<?> packet);

    /**
     * Transform x, y, z in sendToAround if they are in ship space.
     */
    @Inject(
        method = "sendToAround",
        at = @At("HEAD"),
        cancellable = true
    )
    private void sendToAround(@Nullable final PlayerEntity player, final double x, final double y, final double z,
        final double distance, final RegistryKey<World> worldKey, final Packet<?> packet, final CallbackInfo ci) {
        final World world = server.getWorld(worldKey);
        if (world == null) {
            return;
        }

        final ShipObject ship = VSGameUtilsKt.getShipObjectManagingPos(world, (int) x >> 4, (int) z >> 4);
        if (ship == null) {
            return;
        }

        // position in-world
        final Vector3d p = VSGameUtilsKt.toWorldCoordinates(ship.getShipData(), x, y, z);

        // re-call with correct x, y, z and return
        sendToAround(player, p.x, p.y, p.z, distance, worldKey, packet);
        ci.cancel();
    }

}
