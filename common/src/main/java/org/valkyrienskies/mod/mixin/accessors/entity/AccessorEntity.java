package org.valkyrienskies.mod.mixin.accessors.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface AccessorEntity {

    @Accessor("pos")
    void setPosNoUpdates(Vec3d pos);

}
