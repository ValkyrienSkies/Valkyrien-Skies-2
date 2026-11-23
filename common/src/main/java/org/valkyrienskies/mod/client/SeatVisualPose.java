package org.valkyrienskies.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.IdentityHashMap;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/** One direct-seat model-to-world pose, including the renderer's possibly random local offset. */
public final class SeatVisualPose {
    public final Vec3 offset;
    public final Matrix4dc basis;
    private final Quaternionf rotation;
    private final Vector3dc scale;

    public SeatVisualPose(Vector3dc origin, Quaterniondc rotation, Vector3dc scale, Vec3 offset) {
        this.offset = offset;
        this.basis = new Matrix4d().translation(origin).rotate(rotation).scale(scale).translate(offset.x, offset.y, offset.z);
        this.rotation = new Quaternionf(rotation);
        this.scale = new Vector3d(scale);
    }

    public static SeatVisualPose sample(Entity actor, float t) {
        var seat = VSGameUtilsKt.getShipMountedToData(actor, t);
        if (seat == null || !(seat.getShipMountedTo() instanceof ClientShip ship)) return null;
        var transform = ship.getRenderTransform();
        var origin = transform.getShipToWorld().transformPosition(seat.getMountPosInShip(), new Vector3d());
        var offset = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(actor).getRenderOffset(actor, t);
        return new SeatVisualPose(origin, transform.getShipToWorldRotation(), transform.getShipToWorldScaling(), offset);
    }

    public void apply(PoseStack stack, double dx, double dy, double dz) {
        stack.translate(basis.m30() + dx, basis.m31() + dy, basis.m32() + dz);
        stack.mulPose(rotation);
        stack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }

    public AABB bounds(AABB local) {
        return VectorConversionsMCKt.toMinecraft(VectorConversionsMCKt.toJOML(local).transform(basis));
    }

    public static final class Samples<K> {
        private final IdentityHashMap<K, SeatVisualPose> poses = new IdentityHashMap<>();
        public SeatVisualPose get(K actor, Supplier<SeatVisualPose> sample) {
            // null (not a seat) is also a result; do not retry a seat lookup within the same view.
            if (!poses.containsKey(actor)) poses.put(actor, sample.get());
            return poses.get(actor);
        }
    }
}
