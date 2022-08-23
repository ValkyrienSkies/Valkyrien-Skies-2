package org.valkyrienskies.mod.mixin.world.entity;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.util.MinecraftPlayer;
import org.valkyrienskies.mod.mixinducks.world.entity.PlayerDuck;

@Mixin(Player.class)
public class MixinPlayer implements PlayerDuck {

    @Unique
    private final MinecraftPlayer vsPlayer = new MinecraftPlayer(Player.class.cast(this));

    @Override
    public MinecraftPlayer vs_getPlayer() {
        return vsPlayer;
    }
}
