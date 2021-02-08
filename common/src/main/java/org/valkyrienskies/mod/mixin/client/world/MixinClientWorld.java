package org.valkyrienskies.mod.mixin.client.world;

import net.minecraft.client.world.ClientWorld;
import org.valkyrienskies.mod.IShipObjectWorldProvider;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObjectWorld;

@Mixin(ClientWorld.class)
public class MixinClientWorld implements IShipObjectWorldProvider {

    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }
}
