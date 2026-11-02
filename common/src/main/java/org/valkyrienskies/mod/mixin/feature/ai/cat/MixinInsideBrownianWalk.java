package org.valkyrienskies.mod.mixin.feature.ai.cat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ShipAwareCollisionUtil;

// InsideBrownianWalk's noCollision gate ("is the mob under cover") reads world blocks only; for a cat indoors on a ship, world cells are air.
@Mixin(InsideBrownianWalk.class)
public abstract class MixinInsideBrownianWalk {

    @WrapOperation(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/entity/Entity;)Z"
        )
    )
    private static boolean vs$noCollisionIncludingShips(
        final ServerLevel level, final Entity entity, final Operation<Boolean> original
    ) {
        return ShipAwareCollisionUtil.noCollisionIncludingShips(level, entity, entity.getBoundingBox());
    }
}
