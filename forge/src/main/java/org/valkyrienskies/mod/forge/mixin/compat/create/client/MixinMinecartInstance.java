package org.valkyrienskies.mod.forge.mixin.compat.create.client;

/*
import com.jozufozu.flywheel.api.MaterialManager;
import com.jozufozu.flywheel.backend.instancing.entity.EntityInstance;
import com.jozufozu.flywheel.util.AnimationTickHolder;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.jozufozu.flywheel.vanilla.MinecartInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(MinecartInstance.class)
public abstract class MixinMinecartInstance extends EntityInstance {


    public MixinMinecartInstance(MaterialManager materialManager, Entity entity) {
        super(materialManager, entity);
    }

    @Redirect(
            method = "beginFrame", at = @At(value = "INVOKE",
            target = "Lcom/jozufozu/flywheel/util/transform/TransformStack;translate(DDD)Ljava/lang/Object;", ordinal = 0),
            remap = false
    )
    private Object redirectTranslate(TransformStack instance, double x, double y, double z) {
        final float partialTicks = AnimationTickHolder.getPartialTicks();
        if (VSGameUtilsKt.getShipManaging(entity) instanceof ClientShip ship) {
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(materialManager.getOriginCoordinate());
            final Vec3 pos = entity.position();
            final Vector3d newPosition =
                    new Vector3d(
                            Mth.lerp(partialTicks, entity.xOld, pos.x),
                            Mth.lerp(partialTicks, entity.yOld, pos.y),
                            Mth.lerp(partialTicks, entity.zOld, pos.z)
                    );
            final ShipTransform transform = ship.getRenderTransform();
            Matrix4d renderMatrix = new Matrix4d()
                    .translate(origin.mul(-1))
                    .mul(transform.getShipToWorld())
                    .translate(newPosition);
            Matrix4f mat4f = new Matrix4f(renderMatrix);
            ((PoseStack) instance).last().pose().mul(mat4f);
        } else {
            instance.translate(x, y, z);
        }
        return null;
    }
}
 */
