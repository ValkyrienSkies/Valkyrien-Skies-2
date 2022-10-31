package org.valkyrienskies.mod.mixin.feature.commands;

import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.commands.VSCommandSource;
import org.valkyrienskies.core.game.ships.ShipObjectWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(CommandSourceStack.class)
public abstract class MixinCommandSourceStack implements VSCommandSource {

    @Shadow
    @Nullable
    public abstract Entity getEntity();

    @Shadow
    public abstract ServerLevel getLevel();

    @NotNull
    @Override
    public ShipObjectWorld<?> getShipWorld() {
        return VSGameUtilsKt.getShipObjectWorld(getLevel());
    }

    @Override
    public Vector3d getCalledAt() {
        final Entity entity = this.getEntity();

        if (entity != null) {
            return VectorConversionsMCKt.toJOML(entity.position());
        } else {
            return null;
        }
    }
}
