package org.valkyrienskies.mod.mixin.accessors.util.math;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Matrix4f.class)
public interface Matrix4fAccessor {
    // region Setters
    @Accessor("m00")
    void setM00(float a00);

    @Accessor("m01")
    void setM01(float a01);

    @Accessor("m02")
    void setM02(float a02);

    @Accessor("m03")
    void setM03(float a03);

    @Accessor("m10")
    void setM10(float a10);

    @Accessor("m11")
    void setM11(float a11);

    @Accessor("m12")
    void setM12(float a12);

    @Accessor("m13")
    void setM13(float a13);

    @Accessor("m20")
    void setM20(float a20);

    @Accessor("m21")
    void setM21(float a21);

    @Accessor("m22")
    void setM22(float a22);

    @Accessor("m23")
    void setM23(float a23);

    @Accessor("m30")
    void setM30(float a30);

    @Accessor("m31")
    void setM31(float a31);

    @Accessor("m32")
    void setM32(float a32);

    @Accessor("m33")
    void setM33(float a33);

    // endregion
    // region Getters
    @Accessor("m00")
    float getM00();

    @Accessor("m01")
    float getM01();

    @Accessor("m02")
    float getM02();

    @Accessor("m03")
    float getM03();

    @Accessor("m10")
    float getM10();

    @Accessor("m11")
    float getM11();

    @Accessor("m12")
    float getM12();

    @Accessor("m13")
    float getM13();

    @Accessor("m20")
    float getM20();

    @Accessor("m21")
    float getM21();

    @Accessor("m22")
    float getM22();

    @Accessor("m23")
    float getM23();

    @Accessor("m30")
    float getM30();

    @Accessor("m31")
    float getM31();

    @Accessor("m32")
    float getM32();

    @Accessor("m33")
    float getM33();
    // endregion
}
