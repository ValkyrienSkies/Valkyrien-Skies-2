package org.valkyrienskies.mod.forge.mixin.entity;

import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContainerEntity.class)
public interface MixinContainerEntity extends Container, MenuProvider {
    @Shadow
    boolean isRemoved();

    @Shadow
    Vec3 position();

    /**
     * @author Bunting_chj
     * @reason This is to restore Entities with storage spaces interactibility.
     */
    @Overwrite
    default boolean isChestVehicleStillValid(Player player) {
        if(this.isRemoved()) return false;
        return VSGameUtilsKt.squaredDistanceToInclShips(player, this.position().x, this.position().y, this.position().z) <= 8.0F * 8.0F;
    }
}
