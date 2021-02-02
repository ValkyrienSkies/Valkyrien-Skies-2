package org.valkyrienskies.mod.mixin.client.multiplayer;

import org.valkyrienskies.mod.IShipObjectWorldProvider;
import org.valkyrienskies.mod.IShipObjectWorldProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObjectWorld;
import org.valkyrienskies.mod.IShipObjectWorldProvider;

@Mixin(ClientLevel.class)
public class MixinClientLevel implements IShipObjectWorldProvider {

    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }
}
