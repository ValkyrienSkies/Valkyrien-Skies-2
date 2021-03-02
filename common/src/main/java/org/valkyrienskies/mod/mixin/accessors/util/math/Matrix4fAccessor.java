package org.valkyrienskies.mod.mixin.accessors.util.math;

import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Matrix4f.class)
public interface Matrix4fAccessor {
    // region Setters
    @Accessor("a00")
    void setA00(float a00);
    @Accessor("a01")
    void setA01(float a01);
    @Accessor("a02")
    void setA02(float a02);
    @Accessor("a03")
    void setA03(float a03);
    @Accessor("a10")
    void setA10(float a10);
    @Accessor("a11")
    void setA11(float a11);
    @Accessor("a12")
    void setA12(float a12);
    @Accessor("a13")
    void setA13(float a13);
    @Accessor("a20")
    void setA20(float a20);
    @Accessor("a21")
    void setA21(float a21);
    @Accessor("a22")
    void setA22(float a22);
    @Accessor("a23")
    void setA23(float a23);
    @Accessor("a30")
    void setA30(float a30);
    @Accessor("a31")
    void setA31(float a31);
    @Accessor("a32")
    void setA32(float a32);
    @Accessor("a33")
    void setA33(float a33);
    // endregion
    // region Getters
    @Accessor("a00")
    float getA00();
    @Accessor("a01")
    float getA01();
    @Accessor("a02")
    float getA02();
    @Accessor("a03")
    float getA03();
    @Accessor("a10")
    float getA10();
    @Accessor("a11")
    float getA11();
    @Accessor("a12")
    float getA12();
    @Accessor("a13")
    float getA13();
    @Accessor("a20")
    float getA20();
    @Accessor("a21")
    float getA21();
    @Accessor("a22")
    float getA22();
    @Accessor("a23")
    float getA23();
    @Accessor("a30")
    float getA30();
    @Accessor("a31")
    float getA31();
    @Accessor("a32")
    float getA32();
    @Accessor("a33")
    float getA33();
    // endregion
}
