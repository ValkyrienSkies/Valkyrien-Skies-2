package org.valkyrienskies.mod.compat.flywheel;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.entity.Entity;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class EntityVisualizerDecorator<T extends Entity> implements EntityVisualizer<T> {

    private EntityVisualizer<? super T> inner;

    public EntityVisualizerDecorator(EntityVisualizer<? super T> visualizer){
        this.inner = visualizer;
    }

    @Override
    public EntityVisual<? super T> createVisual(VisualizationContext ctx, T entity, float partialTick) {
        if(VSGameUtilsKt.getShipManaging(entity) instanceof ClientShip ship){
            VisualEmbedding embedding = ShipEmbeddingManager.INSTANCE.getOrCreateEmbedding(ship, ctx);
            return inner.createVisual(embedding, entity, partialTick);
        }
        else return inner.createVisual(ctx, entity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(T entity) {
        return inner.skipVanillaRender(entity);
    }
}
