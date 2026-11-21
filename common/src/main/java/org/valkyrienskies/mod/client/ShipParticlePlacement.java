package org.valkyrienskies.mod.client;

import net.minecraft.client.Minecraft;
import java.util.List;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;
import org.valkyrienskies.mod.common.ShipEffectLocations;
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames;

public final class ShipParticlePlacement {
    private ShipParticlePlacement() {}

    public static ShipPhysicalPiece piece(List<ShipPhysicalPiece> pieces, double x, double y, double z) {
        return ShipEffectLocations.point(pieces.stream().map(ShipPieceRenderFrames::rendered).toList(), x, y, z,
            Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }

    public static Vector3d velocity(ShipPhysicalPiece piece, ClientShip ship, double x, double y, double z) {
        final var local = ship.getRenderTransform().getWorldToShip().transformDirection(new Vector3d(ship.getVelocity())).mul(0.05);
        return piece.getShipToWorld().transformDirection(local.add(x, y, z));
    }
}
