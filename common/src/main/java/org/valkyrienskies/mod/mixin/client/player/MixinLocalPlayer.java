package org.valkyrienskies.mod.mixin.client.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends LivingEntity {
    protected MixinLocalPlayer(final EntityType<? extends LivingEntity> entityType,
        final Level level) {
        super(entityType, level);
    }

    /**
     * @reason We need to overwrite this method to force Minecraft to smoothly interpolate the Y rotation of the player
     *         during rendering. Why it wasn't like this originally is beyond me \(>.<)/
     * @author StewStrong
     */
    @Overwrite
    public float getViewYRot(final float partialTick) {
        if (this.isPassenger()) {
            return super.getViewYRot(partialTick);
        }
        return Mth.lerp(partialTick, this.yRotO, this.yRot);
    }
}
