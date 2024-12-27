package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(Player.class)
public class MixinPlayer implements PlayerDuck {

    @Unique
    private final MinecraftPlayer vsPlayer = new MinecraftPlayer(Player.class.cast(this));

    @Unique
    private Vec3 queuedPositionUpdate = null;

    @Unique
    private boolean handledMovePacket = false;

    @Override
    public MinecraftPlayer vs_getPlayer() {
        return vsPlayer;
    }

    @Override
    public Vec3 vs_getQueuedPositionUpdate() {
        return this.queuedPositionUpdate;
    }

    @Override
    public void vs_setQueuedPositionUpdate(Vec3 queuedPositionUpdate) {
        this.queuedPositionUpdate = queuedPositionUpdate;
    }

    @Override
    public boolean vs_handledMovePacket() {
        return this.handledMovePacket;
    }

    @Override
    public void vs_setHandledMovePacket(boolean handledMovePacket) {
        this.handledMovePacket = handledMovePacket;
    }
}
