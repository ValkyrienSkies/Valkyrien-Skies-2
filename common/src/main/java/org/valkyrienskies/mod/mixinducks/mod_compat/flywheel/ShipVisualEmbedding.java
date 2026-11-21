package org.valkyrienskies.mod.mixinducks.mod_compat.flywheel;

import java.util.List;
import net.minecraft.core.Vec3i;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;

public interface ShipVisualEmbedding {
    void vs$setShip(ClientShip ship);
    void vs$setPieces(List<ShipPhysicalPiece> pieces);
    List<ShipPhysicalPiece> vs$pieces();
    void vs$piecePose(ShipPhysicalPiece piece, Vec3i origin, Matrix4d dest, Matrix3f normals);
}
