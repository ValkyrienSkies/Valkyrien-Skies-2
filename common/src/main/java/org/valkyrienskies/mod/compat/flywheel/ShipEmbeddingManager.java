package org.valkyrienskies.mod.compat.flywheel;

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

public class ShipEmbeddingManager {

    public static final ShipEmbeddingManager INSTANCE = new ShipEmbeddingManager();

    protected static ConcurrentHashMap<ClientShip, Vec3i> vs$shipAnchor = new ConcurrentHashMap<>();

    protected static ConcurrentHashMap<ClientShip, Vec3i> vs$EmbeddingOrigin = new ConcurrentHashMap<>();

    protected static ConcurrentHashMap<ClientShip, VisualEmbedding> vs$shipEmbedding = new ConcurrentHashMap<>();

    private ShipEmbeddingManager(){
        ShipUnloadEventClient.Companion.on(event -> this.unloadShip(event.getShip()));
        VSGameEvents.INSTANCE.getRenderShip().on(event -> this.updateAllShips());
    }

    /*
        Get or Create a VisualEmbedding that is attached to the ship.
     */
    public synchronized VisualEmbedding getOrCreateEmbedding(ClientShip ship, VisualizationContext ctx){
        return vs$shipEmbedding.computeIfAbsent(ship, s ->{
            BlockPos anchor = BlockPos.containing(VectorConversionsMCKt.toMinecraft(s.getRenderTransform().getPositionInShip()));
            Vec3i origin = ctx.renderOrigin();
            VisualEmbedding result = ctx.createEmbedding(anchor);
            setEmbeddingTransform(result, s, anchor, origin);
            vs$shipAnchor.put(s, anchor);
            vs$EmbeddingOrigin.put(s, origin);
            return result;
        });
    }

    /*
        Updates every VisualEmbedding attached to a ship.
        This should be called manually to update the transformation, or it won't properly update current ship movement.
     */
    protected void updateAllShips() {
        for(final ClientShip ship : vs$shipEmbedding.keySet()){
            final Vec3i anchor = vs$shipAnchor.get(ship);
            final VisualEmbedding embedding = vs$shipEmbedding.get(ship);
            final Vec3i origin = vs$EmbeddingOrigin.get(ship);
            setEmbeddingTransform(embedding, ship, anchor, origin);
        }
    }
    /*
        Removes ship from the storage.
        This will delete the embedding create for the ship.
     */
    protected synchronized void unloadShip(ClientShip ship) {
        final VisualEmbedding embedding = vs$shipEmbedding.remove(ship);
        if(embedding != null){
            embedding.delete();
        }
        vs$shipAnchor.remove(ship);
        vs$EmbeddingOrigin.remove(ship);
    }

    protected static void setEmbeddingTransform(VisualEmbedding embedding, ClientShip ship, Vec3i anchor,
        Vec3i origin){
        final Matrix4f poseMatrix = new Matrix4f();
        final Matrix3f normalMatrix = new Matrix3f();
        poseMatrix.translate(ship.getRenderTransform().getShipToWorld().transformPosition(anchor.getX(), anchor.getY(), anchor.getZ(), new Vector3d()).get(new Vector3f()));
        poseMatrix.translate(new Vector3f(-origin.getX(), -origin.getY(), -origin.getZ()));
        poseMatrix.rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        normalMatrix.set(poseMatrix);
        embedding.transforms(poseMatrix, normalMatrix);
    }
}
