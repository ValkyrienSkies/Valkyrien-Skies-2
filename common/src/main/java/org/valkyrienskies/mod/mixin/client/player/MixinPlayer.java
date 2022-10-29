package org.valkyrienskies.mod.mixin.client.player;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {
    protected MixinPlayer(final EntityType<? extends LivingEntity> entityType,
        final Level level) {
        super(entityType, level);
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        ),
        method = "aiStep"
    )
    private List<Entity> redirectAiStep(final Level instance, final Entity entity, final AABB aabb) {
        if (this.getVehicle() instanceof ShipMountingEntity) {
            return new ArrayList<>();
        }

        return instance.getEntities(entity, aabb);
    }
}
