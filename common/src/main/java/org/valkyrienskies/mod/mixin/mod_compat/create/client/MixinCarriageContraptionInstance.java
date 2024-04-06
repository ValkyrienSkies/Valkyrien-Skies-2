package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.jozufozu.flywheel.api.MaterialManager;
import com.jozufozu.flywheel.backend.instancing.entity.EntityInstance;
import com.jozufozu.flywheel.util.AnimationTickHolder;
import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(CarriageContraptionInstance.class)
public abstract class MixinCarriageContraptionInstance extends EntityInstance {

    public MixinCarriageContraptionInstance(MaterialManager materialManager, Entity entity) {
        super(materialManager, entity);
    }

    @Redirect(
        method = "beginFrame",
        at = @At(value = "INVOKE", target = "Lcom/jozufozu/flywheel/util/transform/TransformStack;translate(Lcom/mojang/math/Vector3f;)Ljava/lang/Object;"), remap = false
    )
    private Object redirectTranslate(final TransformStack instance, final Vector3f vector3f) {

        final float partialTicks = AnimationTickHolder.getPartialTicks();
        final Level level = this.world;
        final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(level, vector3f.x(), vector3f.y(), vector3f.z());

        if (ship != null) {
            final CarriageContraptionEntity carriageContraptionEntity = (CarriageContraptionEntity) this.entity;
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(this.materialManager.getOriginCoordinate());
            final Vec3 pos = carriageContraptionEntity.position();
            final Vector3d newPosition =
                    new Vector3d(
                            Mth.lerp(partialTicks, carriageContraptionEntity.xOld, pos.x),
                            Mth.lerp(partialTicks, carriageContraptionEntity.yOld, pos.y),
                            Mth.lerp(partialTicks, carriageContraptionEntity.zOld, pos.z)
                    );
            final ShipTransform transform = ship.getRenderTransform();
            Matrix4d renderMatrix = new Matrix4d()
                    .translate(origin.mul(-1))
                    .mul(transform.getShipToWorld())
                    .translate(newPosition);
            Matrix4f mat4f = VectorConversionsMCKt.toMinecraft(renderMatrix);
            ((PoseStack) instance).last().pose().multiply(mat4f);
        } else {
            instance.translate(vector3f);
        }
        return null;
    }
}
