package org.valkyrienskies.mod.forge.mixin.compat.flywheel.client;

import com.jozufozu.flywheel.backend.instancing.IDynamicInstance;
import com.jozufozu.flywheel.backend.instancing.ITickableInstance;
import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(InstanceManager.class)
public class MixinInstanceManager {

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/backend/instancing/IDynamicInstance;getWorldPosition()Lnet/minecraft/core/BlockPos;",
            remap = false
        ),
        method = "*",
        remap = false
    )
    private BlockPos redirectGetWorldPos1(final IDynamicInstance receiver) {
        final Vector3d v = VSGameUtils.getWorldCoordinates(Minecraft.getInstance().level,
            VectorConversionsMCKt.toJOMLD(receiver.getWorldPosition()));

        return new BlockPos(v.x, v.y, v.z);
    }

    @Redirect(
        at = @At(
            value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/backend/instancing/ITickableInstance;getWorldPosition()Lnet/minecraft/core/BlockPos;",
            remap = false
        ),
        method = "*",
        remap = false
    )
    private BlockPos redirectGetWorldPos2(final ITickableInstance receiver) {
        final Vector3d v = VSGameUtils.getWorldCoordinates(Minecraft.getInstance().level,
            VectorConversionsMCKt.toJOMLD(receiver.getWorldPosition()));

        return new BlockPos(v.x, v.y, v.z);
    }

}
