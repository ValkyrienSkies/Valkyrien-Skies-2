package org.valkyrienskies.mod.mixin.server.world;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChunkMap.TrackedEntity.class)
public class MixinChunkMap$TrackedEntity {

    @Shadow
    @Final
    private Entity entity;

    @Unique
    private Ship inCallShip = null;

    // Changes entity position for tracking into world space if needed
    @Redirect(method = "updatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerEntity;sentPos()Lnet/minecraft/world/phys/Vec3;"))
    Vec3 includeShips(final ServerEntity instance) {
        final Vector3d pos = VectorConversionsMCKt.toJOML(instance.sentPos());
        final Ship ship = inCallShip = VSGameUtilsKt.getShipObjectManagingPos(this.entity.level, pos);
        if (ship != null) {
            return VectorConversionsMCKt.toMinecraft(ship.getShipTransform()
                .getShipToWorldMatrix().transformPosition(pos));
        } else {
            return instance.sentPos();
        }
    }

    @Redirect(method = "updatePlayer", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/Entity;forcedLoading:Z"))
    boolean skipWierdCheck(final Entity instance) {
        return inCallShip != null || instance.forcedLoading;
    }

}
