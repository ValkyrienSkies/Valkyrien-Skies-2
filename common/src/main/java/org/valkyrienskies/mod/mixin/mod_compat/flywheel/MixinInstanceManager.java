// package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

// import com.jozufozu.flywheel.api.instance.DynamicInstance;
// import com.jozufozu.flywheel.api.instance.TickableInstance;
// import com.jozufozu.flywheel.backend.instancing.InstanceManager;
// import net.minecraft.client.Minecraft;
// import net.minecraft.core.BlockPos;
// import org.joml.Vector3d;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Pseudo;
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Redirect;
// import org.valkyrienskies.mod.common.VSGameUtilsKt;
// import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

// @Pseudo
// @Mixin(InstanceManager.class)
// public class MixinInstanceManager {

//     @Redirect(
//         at = @At(
//             value = "INVOKE",
//             target = "Lcom/jozufozu/flywheel/api/instance/DynamicInstance;getWorldPosition()Lnet/minecraft/core/BlockPos;"
//         ),
//         method = "*"
//     )
//     private BlockPos redirectGetWorldPos1(final DynamicInstance receiver) {
//         final Vector3d v = VSGameUtilsKt.getWorldCoordinates(
//             Minecraft.getInstance().level,
//             receiver.getWorldPosition(),
//             VectorConversionsMCKt.toJOMLD(receiver.getWorldPosition())
//         );

//         return BlockPos.containing(v.x, v.y, v.z);
//     }

//     @Redirect(
//         at = @At(
//             value = "INVOKE",
//             target = "Lcom/jozufozu/flywheel/api/instance/TickableInstance;getWorldPosition()Lnet/minecraft/core/BlockPos;"
//         ),
//         method = "*"
//     )
//     private BlockPos redirectGetWorldPos2(final TickableInstance receiver) {
//         final Vector3d v = VSGameUtilsKt.getWorldCoordinates(Minecraft.getInstance().level,
//             receiver.getWorldPosition(),
//             VectorConversionsMCKt.toJOMLD(receiver.getWorldPosition()));

//         return BlockPos.containing(v.x, v.y, v.z);
//     }

// }
