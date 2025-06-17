// package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

// import com.jozufozu.flywheel.backend.RenderLayer;
// import com.jozufozu.flywheel.backend.instancing.instancing.InstancedMaterialGroup;
// import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
// import com.jozufozu.flywheel.core.shader.WorldProgram;
// import java.util.stream.Stream;
// import org.jetbrains.annotations.Nullable;
// import org.joml.Matrix4f;
// import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Pseudo;
// import org.spongepowered.asm.mixin.Shadow;
// import org.valkyrienskies.mod.mixinducks.MixinInstancingEngineDuck;

// @Pseudo
// @Mixin(value = InstancingEngine.class, remap = false)
// public abstract class MixinInstancingEngine<P extends WorldProgram> implements MixinInstancingEngineDuck {
//     @Shadow
//     protected abstract Stream<InstancedMaterialGroup<P>> getGroupsToRender(@Nullable RenderLayer layer);

//     @Override
//     public void vs$render(final Matrix4f viewProjection, final double camX, final double camY, final double camZ,
//         final RenderLayer layer) {
//         this.getGroupsToRender(layer).forEach(g -> g.render(viewProjection, camX, camY, camZ, layer));
//     }
// }
