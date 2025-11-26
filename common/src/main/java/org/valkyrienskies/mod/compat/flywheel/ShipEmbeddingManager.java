package org.valkyrienskies.mod.compat.flywheel;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntitySection;
import org.valkyrienskies.core.impl.hooks.VSEvents.ShipUnloadEventClient;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This class is responsible for managing the VisualEmbeddings for the ships.
 * VisualEmbedding is a sub-interface of VisualizationContext in flywheel,
 * and it can be transformed to arbitrary pose to propagate it to visuals associated to it.
 * Be aware that currently in flywheel 1.0.4,
 * method getVisualPosition() in flywheel doesn't seem to take VisualEmbedding's transform into calculation.
 * @author Bunting_chj
 */
public class ShipEmbeddingManager {

    public static final ShipEmbeddingManager INSTANCE = new ShipEmbeddingManager();

    protected static ConcurrentHashMap<ClientShip, Vec3i> vs$shipAnchor = new ConcurrentHashMap<>();

    protected static ConcurrentHashMap<ClientShip, Vec3i> vs$EmbeddingOrigin = new ConcurrentHashMap<>();

    protected static ConcurrentHashMap<ClientShip, VisualEmbedding> vs$shipEmbedding = new ConcurrentHashMap<>();

    protected static ConcurrentHashMap<BlockEntity, ClientShip> vs$shipBEs = new ConcurrentHashMap<>();

    private ShipEmbeddingManager(){
        ShipUnloadEventClient.Companion.on(event -> this.unloadShip(event.getShip()));
        VSGameEvents.INSTANCE.getShipsStartRendering().on(event -> this.updateAllShips());
        VSGameEvents.INSTANCE.getShipsStartRenderingSodium().on(event -> this.updateAllShips());
        VSGameEvents.INSTANCE.getEntitySectionSetShip().on(event -> this.updateEntitySection(event.getSection()));
    }

    private void updateEntitySection(EntitySection<?> section){
        VisualizationManager manager = VisualizationManager.get(Minecraft.getInstance().level);
        if (manager == null) return;
        section.getEntities().forEach(
            entity -> {
                manager.entities().queueRemove((Entity) entity);
                manager.entities().queueAdd((Entity) entity);
            }
        );
    }

    /**
     * Get or Create a VisualEmbedding that is attached to the ship.
     * If the pre-existing one is invalid, delete it and create a new one.
     * @author Bunting_chj
     */
    public synchronized VisualEmbedding getOrCreateEmbedding(ClientShip ship, VisualizationContext ctx){
        VisualEmbedding prevEmbedding = vs$shipEmbedding.get(ship);
        if(prevEmbedding != null && ctx.renderOrigin().equals(vs$EmbeddingOrigin.get(ship))) return prevEmbedding;

        // remove previous mapping of visuals and embedding
        vs$shipBEs.entrySet().removeIf(
            entry -> {
                final VisualizationManager manager = VisualizationManager.get(entry.getKey().getLevel());
                if (entry.getValue() == ship && manager != null){
                    manager.blockEntities().queueRemove(entry.getKey());
                    manager.blockEntities().queueAdd(entry.getKey());
                    return true;
                } else return false;
            }
        );
        if(prevEmbedding != null) prevEmbedding.delete();

        BlockPos anchor = BlockPos.containing(VectorConversionsMCKt.toMinecraft(ship.getRenderTransform().getPositionInShip()));
        Vec3i origin = ctx.renderOrigin();
        VisualEmbedding result = ctx.createEmbedding(anchor);
        setEmbeddingTransform(result, ship, anchor, origin);
        vs$shipAnchor.put(ship, anchor);
        vs$EmbeddingOrigin.put(ship, origin);
        vs$shipEmbedding.put(ship, result);
        return result;
    }

    /**
     * Updates every VisualEmbedding attached to a ship.
     * This should be called manually to update the transformation, or it won't properly update current ship movement.
     * Ideally this should be called every frame when ships have their transform changed, so it's bound to ShipStartRendering event.
     * @author Bunting_chj
     */
    public synchronized void updateAllShips() {
        for(final ClientShip ship : vs$shipEmbedding.keySet()){
            final Vec3i anchor = vs$shipAnchor.get(ship);
            final VisualEmbedding embedding = vs$shipEmbedding.get(ship);
            final Vec3i origin = vs$EmbeddingOrigin.get(ship);
            setEmbeddingTransform(embedding, ship, anchor, origin);
        }
    }
    /**
     * Removes ship from the storage.
     * This will delete the embedding created for the ship, and Visuals too.
     * @param ship The ship to be unloaded.
     * @author Bunting_chj
     */
    public synchronized void unloadShip(ClientShip ship) {
        final VisualEmbedding embedding = vs$shipEmbedding.remove(ship);
        if(embedding != null){
            embedding.delete();
        }
        vs$shipBEs.entrySet().removeIf(
            entry -> {
                if (entry.getValue() == ship) {
                    VisualizationManager.get(entry.getKey().getLevel()).blockEntities().queueRemove(entry.getKey());
                    return true;
                } else return false;
            }
        );
        vs$shipAnchor.remove(ship);
        vs$EmbeddingOrigin.remove(ship);
    }

    /**
     * Removes Every ship and ship-visual from the storage.
     * The main purpose of this method is to flush all the data,
     * current usage is on Flywheel Reload which happens at backend swap.
     * @author Bunting_chj
     */

    public synchronized void unloadAllShip() {
        vs$shipBEs.clear();
        vs$shipEmbedding.clear();
        vs$shipAnchor.clear();
        vs$EmbeddingOrigin.clear();
    }

    /**
     * Updates the embedding created for the ship.
     * It will apply the transformation in seperate elements, to avoid floating point error.
     * @param embedding Visual Embedding that is connected to the ship.
     * @param ship The ship associated to the embedding. Its render origin is anchor.
     * @param anchor 'Absolute' origin of the ship in shipyard.
     *               The ship's position defined in ShipTransform can change its position upon center of mass moving.
     *               Therefore, you must declare a Vec3i(BlockPos) in the shipyard as the ship's anchor,
     *               and the anchor should also be the render origin of embedding too.
     * @param origin render origin of the VisualizationContext that is the parent of the embedding.
     * @author Bunting_chj
     */
    protected static void setEmbeddingTransform(VisualEmbedding embedding, ClientShip ship, Vec3i anchor,
        Vec3i origin){
        final Matrix4f poseMatrix = new Matrix4f();
        final Matrix3f normalMatrix = new Matrix3f();
        poseMatrix.translate(new Vector3f(-origin.getX(), -origin.getY(), -origin.getZ()));
        poseMatrix.translate(ship.getRenderTransform().getShipToWorld().transformPosition(anchor.getX(), anchor.getY(), anchor.getZ(), new Vector3d()).get(new Vector3f()));
        poseMatrix.rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        poseMatrix.scale(ship.getRenderTransform().getShipToWorldScaling().get(new Vector3f()));
        normalMatrix.set(poseMatrix);
        embedding.transforms(poseMatrix, normalMatrix);
    }

    public void register(BlockEntity blockEntity, ClientShip ship) {
        vs$shipBEs.put(blockEntity, ship);
    }
}
