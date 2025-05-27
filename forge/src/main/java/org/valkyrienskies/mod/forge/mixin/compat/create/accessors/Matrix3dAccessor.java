package org.valkyrienskies.mod.forge.mixin.compat.create.accessors;


import com.simibubi.create.foundation.collision.Matrix3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Matrix3d.class)
public interface Matrix3dAccessor {
    @Accessor("m00")
    double getM00();

    @Accessor("m01")
    double getM01();

    @Accessor("m02")
    double getM02();

    @Accessor("m10")
    double getM10();

    @Accessor("m11")
    double getM11();

    @Accessor("m12")
    double getM12();

    @Accessor("m20")
    double getM20();

    @Accessor("m21")
    double getM21();

    @Accessor("m22")
    double getM22();
}

