package org.valkyrienskies.mod.mixin.accessors.world.entity.monster;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Shulker.class)
public interface ShulkerAccessor {
    @Invoker("setAttachFace")
    void vs$setAttachFace(Direction direction);
}
