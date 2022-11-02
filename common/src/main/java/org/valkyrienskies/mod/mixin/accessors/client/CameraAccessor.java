package org.valkyrienskies.mod.mixin.accessors.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker
    void callSetPosition(double x, double y, double z);

}
