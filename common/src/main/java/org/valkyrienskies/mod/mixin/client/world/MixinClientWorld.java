package org.valkyrienskies.mod.mixin.client.world;

import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObjectWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;

import java.util.function.BooleanSupplier;

@Mixin(ClientWorld.class)
public class MixinClientWorld implements IShipObjectWorldProvider {

    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Tick the ship world
        shipObjectWorld.tickShips();
    }
}
