package org.valkyrienskies.mod.mixin.feature.entity_rubberband_fix;

import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.mixinducks.feature.fix_entity_rubberband.ClientboundTeleportEntityPacketDuck;

@Mixin(ClientboundTeleportEntityPacket.class)
public class MixinClientboundTeleportEntityPacket implements ClientboundTeleportEntityPacketDuck {

    @Mutable
    @Shadow
    @Final
    private double x;
    @Mutable
    @Shadow
    @Final
    private double y;
    @Mutable
    @Shadow
    @Final
    private double z;

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
    public void valkyrienskies$setX(final double xa) {
        this.x = xa;
    }

    @Override
    public void valkyrienskies$setY(final double ya) {
        this.y = ya;
    }

    @Override
    public void valkyrienskies$setZ(final double za) {
        this.z = za;
    }
}
