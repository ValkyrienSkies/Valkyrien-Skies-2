package net.examplemod.mixin.client.multiplayer;

import net.examplemod.IShipObjectWorldProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObjectWorld;

@Mixin(ClientLevel.class)
public class MixinClientLevel implements IShipObjectWorldProvider {

    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }
}
