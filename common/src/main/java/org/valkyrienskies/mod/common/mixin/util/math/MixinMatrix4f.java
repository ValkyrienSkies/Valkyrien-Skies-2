package org.valkyrienskies.mod.common.mixin.util.math;

import net.minecraft.util.math.Matrix4f;
import org.joml.Matrix4dc;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.MixinInterfaces;

@Mixin(Matrix4f.class)
public class MixinMatrix4f implements MixinInterfaces.ISetMatrix4fFromJOML {
    // region shadow
    @Shadow
    protected float a00;
    @Shadow
    protected float a01;
    @Shadow
    protected float a02;
    @Shadow
    protected float a03;
    @Shadow
    protected float a10;
    @Shadow
    protected float a11;
    @Shadow
    protected float a12;
    @Shadow
    protected float a13;
    @Shadow
    protected float a20;
    @Shadow
    protected float a21;
    @Shadow
    protected float a22;
    @Shadow
    protected float a23;
    @Shadow
    protected float a30;
    @Shadow
    protected float a31;
    @Shadow
    protected float a32;
    @Shadow
    protected float a33;
    // endregion

    @Override
    public void vs$setFromJOML(Matrix4dc m) {
        a00 = (float) m.m00();
        a01 = (float) m.m10();
        a02 = (float) m.m20();
        a03 = (float) m.m30();
        a10 = (float) m.m01();
        a11 = (float) m.m11();
        a12 = (float) m.m21();
        a13 = (float) m.m31();
        a20 = (float) m.m02();
        a21 = (float) m.m12();
        a22 = (float) m.m22();
        a23 = (float) m.m32();
        a30 = (float) m.m03();
        a31 = (float) m.m13();
        a32 = (float) m.m23();
        a33 = (float) m.m33();
    }

    @Override
    public void vs$setFromJOML(Matrix4fc m) {
        a00 = m.m00();
        a01 = m.m10();
        a02 = m.m20();
        a03 = m.m30();
        a10 = m.m01();
        a11 = m.m11();
        a12 = m.m21();
        a13 = m.m31();
        a20 = m.m02();
        a21 = m.m12();
        a22 = m.m22();
        a23 = m.m32();
        a30 = m.m03();
        a31 = m.m13();
        a32 = m.m23();
        a33 = m.m33();
    }

}
