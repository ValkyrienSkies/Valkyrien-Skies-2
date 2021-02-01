package net.examplemod.mixin.server.level;

import net.examplemod.IShipObjectWorldProvider;
import net.examplemod.ShipSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ShipObjectWorld;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements IShipObjectWorldProvider {

    private ShipObjectWorld shipObjectWorld = null;
    private ShipSavedData shipSavedData = null;

    @Inject(
            at = @At("TAIL"),
            method = "<init>"
    )
    private void postConstructor(CallbackInfo info) {
        // Load ship data from the world storage
        shipSavedData = getDataStorage().computeIfAbsent(ShipSavedData.Companion::createNewEmptyShipSavedData, ShipSavedData.SAVED_DATA_ID);
        // Make a ship world using the loaded ship data
        shipObjectWorld = new ShipObjectWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator());
    }

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    @Shadow
    public abstract DimensionDataStorage getDataStorage();
}
