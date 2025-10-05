package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.compat.flywheel.BlockEntityVisualizerDecorator;
import org.valkyrienskies.mod.compat.flywheel.EntityVisualizerDecorator;

@Mixin(VisualizerRegistry.class)
public abstract class MixinVisualizerRegistry {
    @WrapMethod(
        method = "setVisualizer(Lnet/minecraft/world/entity/EntityType;Ldev/engine_room/flywheel/api/visualization/EntityVisualizer;)V"
    )
    private static <T extends Entity> void decorateEntityVisualizer(EntityType<T> type, @Nullable EntityVisualizer<? super T> visualizer,
        Operation<Void> original){
        if(visualizer != null){
            original.call(type, new EntityVisualizerDecorator<T>(visualizer));
        } else original.call(type, visualizer);
    }

    @WrapMethod(
        method = "setVisualizer(Lnet/minecraft/world/level/block/entity/BlockEntityType;Ldev/engine_room/flywheel/api/visualization/BlockEntityVisualizer;)V"
    )
    private static <T extends BlockEntity> void decorateBlockEntityVisualizer(BlockEntityType<T> type,
        @Nullable BlockEntityVisualizer<? super T> visualizer, Operation<Void> original){
        if(visualizer != null){
            original.call(type, new BlockEntityVisualizerDecorator<T>(visualizer));
        } else original.call(type, visualizer);
    }
}
