package org.valkyrienskies.mod.compat.flywheel;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.entity.Entity;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * This and BlockEntityVisualizerDecorator will check if the subject to createVisual is on a ship,
 * and bind the correct VisualEmbedding provided by ShipEmbeddingManager to it.
 * It is a decorator class, and should expose the interface.
 * @author Bunting_chj
 */
public class EntityVisualizerDecorator<T extends Entity> implements EntityVisualizer<T> {

    private final EntityVisualizer<? super T> inner;

    public EntityVisualizerDecorator(EntityVisualizer<? super T> visualizer){
        this.inner = visualizer;
    }

    @Override
    public EntityVisual<? super T> createVisual(VisualizationContext ctx, T entity, float partialTick) {
        if(VSGameUtilsKt.getShipManaging(entity) instanceof ClientShip ship){
            VisualEmbedding embedding = ShipEmbeddingManager.INSTANCE.getOrCreateEmbedding(ship, ctx);
            EntityVisual<? super T> visual = inner.createVisual(embedding, entity, partialTick);
            return visual;
        } else return inner.createVisual(ctx, entity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(T entity) {
        return inner.skipVanillaRender(entity);
    }
}
