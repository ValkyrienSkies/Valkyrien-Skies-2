package org.valkyrienskies.mod.compat.flywheel;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * This and EntityVisualizerDecorator will check if the subject to createVisual is on a ship,
 * and bind the correct VisualEmbedding provided by ShipEmbeddingManager to it.
 * It is a decorator class, and should expose the interface.
 * @author Bunting_chj
 */
public class BlockEntityVisualizerDecorator<T extends BlockEntity> implements BlockEntityVisualizer<T> {

    private BlockEntityVisualizer<? super T> inner;

    public BlockEntityVisualizerDecorator(BlockEntityVisualizer<? super T> visualizer){
        this.inner = visualizer;
    }

    @Override
    public BlockEntityVisual<? super T> createVisual(VisualizationContext ctx, T blockEntity, float partialTick) {
        if(VSGameUtilsKt.getShipManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos()) instanceof ClientShip ship){
            final VisualEmbedding embedding = ShipEmbeddingManager.INSTANCE.getOrCreateEmbedding(ship, ctx);
            BlockEntityVisual<? super T> visual = inner.createVisual(embedding, blockEntity, partialTick);
            ShipEmbeddingManager.INSTANCE.register(blockEntity, ship);
            return visual;
        }
        else return inner.createVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(T blockEntity) {
        return inner.skipVanillaRender(blockEntity);
    }
}
