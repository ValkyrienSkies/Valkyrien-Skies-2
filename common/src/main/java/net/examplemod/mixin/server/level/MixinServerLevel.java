package net.examplemod.mixin.server.level;

import net.examplemod.IShipObjectWorldProvider;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObjectWorld;

@Mixin(ServerLevel.class)
public class MixinServerLevel implements IShipObjectWorldProvider {

    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData());

    @Inject(
            at = @At("TAIL"),
            method = "<init>"
    )
    private void inject(CallbackInfo info) {
        // TODO: Load QueryableShipData from disk here
    }

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }
}
