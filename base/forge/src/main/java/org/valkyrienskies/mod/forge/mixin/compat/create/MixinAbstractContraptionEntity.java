package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ContraptionWingProvider;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.forge.common.CreateConversionsKt;

@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity implements ContraptionWingProvider {

    @Unique
    private int wingGroupId = -1;

    @Override
    public int getWingGroupId() {
        return wingGroupId;
    }

    @Override
    public void setWingGroupId(final int wingGroupId) {
        this.wingGroupId = wingGroupId;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void postTick(final CallbackInfo ci) {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Level level = thisAsAbstractContraptionEntity.level;
        if (wingGroupId != -1 && level instanceof final ServerLevel serverLevel) {
            final LoadedServerShip ship = VSGameUtilsKt.getShipObjectManagingPos(serverLevel,
                VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.position()));
            if (ship != null) {
                // This can happen if a player moves a train contraption from ship to world using a wrench
                ship.getAttachment(WingManager.class).setWingGroupTransform(wingGroupId, computeContraptionWingTransform());
            }
        }
    }

    @NotNull
    @Override
    public Matrix4dc computeContraptionWingTransform() {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Matrix3d rotationMatrix =
            CreateConversionsKt.toJOML(thisAsAbstractContraptionEntity.getRotationState().asMatrix());
        final Vector3d pos = VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.position());
        return new Matrix4d(rotationMatrix).setTranslation(pos);
    }
}
