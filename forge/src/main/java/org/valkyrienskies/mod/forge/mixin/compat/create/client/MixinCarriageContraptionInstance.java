package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(CarriageContraptionVisual.class)
public abstract class MixinCarriageContraptionInstance extends ContraptionVisual<CarriageContraptionEntity> {

    public MixinCarriageContraptionInstance(VisualizationContext context, CarriageContraptionEntity entity, float partialTick) {
        super(context, entity, partialTick);
    }

    @WrapOperation(remap = false,
        method = "animate", at = @At(value = "INVOKE",
        target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lorg/joml/Vector3fc;)Ldev/engine_room/flywheel/lib/transform/Translate;")
    )
    private Translate<PoseTransformStack> redirectTranslate(PoseTransformStack instance, Vector3fc vector3f, Operation<Object> operation, float partialTicks) {
        final Level level = this.level;
        final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(level, vector3f.x(), vector3f.y(), vector3f.z());

        if (ship != null) {
            final CarriageContraptionEntity carriageContraptionEntity = this.entity;
            final Vector3d origin = VectorConversionsMCKt.toJOMLD(this.visualizationContext.renderOrigin());
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
            Matrix4f mat4f = new Matrix4f(renderMatrix);
            instance.unwrap().last().pose().mul(mat4f);
        } else {
            operation.call(instance, vector3f);
            // instance.translate(vector3f);
        }
        return instance;
    }
}
