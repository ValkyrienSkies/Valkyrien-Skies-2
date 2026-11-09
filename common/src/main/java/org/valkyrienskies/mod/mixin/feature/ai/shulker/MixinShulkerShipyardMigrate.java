package org.valkyrienskies.mod.mixin.feature.ai.shulker;

import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;

// Migrate the Shulker between world and shipyard frames as it (un)attaches from a ship — vanilla Shulker.tick pins itself to integer cells via setPos(floor+0.5...), and on a moving ship the dragger pushes it through fractional world positions which the snap rounds inconsistently each tick (visible stutter + drift). Once in shipyard frame, the snap rounds shipyard-local fractional offsets (constant for a ship moving smoothly) and the renderer's shipToWorld produces the visible world position without per-tick dragger fixup.
@Mixin(Shulker.class)
public abstract class MixinShulkerShipyardMigrate {

    @Inject(method = "tick", at = @At("HEAD"))
    private void vs$migrateBetweenWorldAndShipyard(final CallbackInfo ci) {
        final Shulker self = (Shulker) (Object) this;
        final Level level = self.level();
        if (level.isClientSide()) return;
        if (self.isPassenger()) return;
        // Shulkers disappear if the ship they're on gets deleted. So do other shipyard entities? Too bad!
        if (VSGameUtilsKt.isBlockInShipyard(level, self.position())) return;

        final Ship ship = VSGameUtilsKt.getEnclosingShip(self);
        if (!(ship instanceof LoadedShip loadedShip)) return;
        DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(self, loadedShip);
    }
}
