package org.valkyrienskies.mod.mixin.util.math;

import net.minecraft.util.math.Matrix4f;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.MixinInterfaces;

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
    public void vs$setFromJOML(Matrix4dc matrix4dc) {
        a00 = (float) matrix4dc.m00();
        a01 = (float) matrix4dc.m10();
        a02 = (float) matrix4dc.m20();
        a03 = (float) matrix4dc.m30();
        a10 = (float) matrix4dc.m01();
        a11 = (float) matrix4dc.m11();
        a12 = (float) matrix4dc.m21();
        a13 = (float) matrix4dc.m31();
        a20 = (float) matrix4dc.m02();
        a21 = (float) matrix4dc.m12();
        a22 = (float) matrix4dc.m22();
        a23 = (float) matrix4dc.m32();
        a30 = (float) matrix4dc.m03();
        a31 = (float) matrix4dc.m13();
        a32 = (float) matrix4dc.m23();
        a33 = (float) matrix4dc.m33();
    }

}
