package org.valkyrienskies.mod.mixin.server.level;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ShipObjectWorld;
import org.valkyrienskies.mod.IShipObjectWorldProvider;
import org.valkyrienskies.mod.ShipSavedData;

@Mixin(ServerWorld.class)
public abstract class MixinServerLevel implements IShipObjectWorldProvider {

    @Shadow public abstract PersistentStateManager getPersistentStateManager();

    private ShipObjectWorld shipObjectWorld = null;
    private ShipSavedData shipSavedData = null;

    @Inject(
            at = @At("TAIL"),
            method = "<init>"
    )
    private void postConstructor(CallbackInfo info) {
        // Load ship data from the world storage
        shipSavedData = getPersistentStateManager()
            .getOrCreate(ShipSavedData.Companion::createNewEmptyShipSavedData, ShipSavedData.SAVED_DATA_ID);
        // Make a ship world using the loaded ship data
        shipObjectWorld = new ShipObjectWorld(shipSavedData.getQueryableShipData(), shipSavedData.getChunkAllocator());
    }

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }


}
