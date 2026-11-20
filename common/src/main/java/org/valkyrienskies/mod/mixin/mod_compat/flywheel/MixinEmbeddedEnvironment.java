package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.backend.engine.embed.EmbeddedEnvironment;
import java.util.List;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4d;
import org.joml.Matrix3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.ShipVisualEmbedding;

@Mixin(value = EmbeddedEnvironment.class, remap = false)
public abstract class MixinEmbeddedEnvironment implements ShipVisualEmbedding {
    @Shadow @Final private EmbeddedEnvironment parent;
    @Shadow @Final private Matrix4f pose;
    @Shadow @Final private Matrix3f normal;
    @Shadow @Final private Vec3i renderOrigin;
    @Unique private ClientShip vs$ship;
    @Unique private volatile List<ShipPhysicalPiece> vs$pieces = List.of();

    @Override
    public void vs$setPieces(List<ShipPhysicalPiece> pieces) {
        vs$pieces = pieces;
    }

    @Override
    public List<ShipPhysicalPiece> vs$pieces() {
        return vs$ship != null || parent == null ? vs$pieces : ((ShipVisualEmbedding) parent).vs$pieces();
    }

    @Override
    public void vs$piecePose(ShipPhysicalPiece piece, Vec3i origin, Matrix4d dest, Matrix3f normals) {
        if (vs$ship == null && parent != null) {
            ((ShipVisualEmbedding) parent).vs$piecePose(piece, origin, dest, normals);
            dest.mul(pose);
            normals.mul(normal);
        } else {
            dest.translation(-origin.getX(), -origin.getY(), -origin.getZ()).mul(piece.getShipToWorld())
                .translate(renderOrigin.getX(), renderOrigin.getY(), renderOrigin.getZ());
            new Matrix4f(dest).normal(normals);
        }
    }

    @Override
    public void vs$setShip(ClientShip ship) {
        this.vs$ship = ship;
    }

    @Inject(method = "composeMatrices", at = @At("RETURN"))
    private void vs$applyShipVisibility(Matrix4f pose, Matrix3f normal, CallbackInfo ci) {
        if (vs$ship != null && !ShipRenderViews.shouldRenderShip(vs$ship)) {
            pose.scale(0.0f);
        }
    }
}
