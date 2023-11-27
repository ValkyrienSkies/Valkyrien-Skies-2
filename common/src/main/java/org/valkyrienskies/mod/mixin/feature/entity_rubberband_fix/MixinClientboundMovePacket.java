package org.valkyrienskies.mod.mixin.feature.entity_rubberband_fix;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.mixinducks.feature.fix_entity_rubberband.ClientboundMoveEntityPacketDuck;

@Mixin(ClientboundMoveEntityPacket.class)
public abstract class MixinClientboundMovePacket implements ClientboundMoveEntityPacketDuck {
    @Mutable
    @Shadow
    @Final
    protected short xa;
    @Mutable
    @Shadow
    @Final
    protected short ya;
    @Mutable
    @Shadow
    @Final
    protected short za;
    @Unique
    public Long shipId;

    @Override
    public Long valkyrienskies$getShipId() {
        return this.shipId;
    }

    @Override
    public void valkyrienskies$setShipId(Long shipId) {
        this.shipId = shipId;
    }

    @Override
    public void valkyrienskies$setXa(final int xa) {
        this.xa = (short) xa;
    }

    @Override
    public void valkyrienskies$setYa(final int ya) {
        this.ya = (short) ya;
    }

    @Override
    public void valkyrienskies$setZa(final int za) {
        this.za = (short) za;
    }
}
