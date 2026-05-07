package org.valkyrienskies.mod.mixin.feature.clip_replace;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.feature.clip_replace.ClipContextDuck;

@Debug(export = true)
@Mixin(BlockGetter.class)
public interface MixinBlockGetter {

    @WrapMethod(method = "clip")
    default BlockHitResult clip(ClipContext clipContext, Operation<BlockHitResult> original) {
        if (!(this instanceof Level level) || (clipContext instanceof ClipContextDuck ccd && ccd.getRayCastExtraParameter().getUseVanillaClip())) {
            return original.call(clipContext);
        }

        if (VSGameUtilsKt.getShipManagingPos(level, clipContext.getTo()) !=
            VSGameUtilsKt.getShipManagingPos(level, clipContext.getFrom())) {
            LogManager.getLogger().warn("Trying to clip from " +
                clipContext.getFrom() + " to " + clipContext.getTo() +
                " wich one of them is in a shipyard wich is ... sus!!");

            final Vec3 vec3 = clipContext.getFrom().subtract(clipContext.getTo());
            return BlockHitResult.miss(
                clipContext.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z),
                BlockPos.containing(clipContext.getTo())
            );
        } else {
            return RaycastUtilsKt.clipIncludeShipsImpl(level, clipContext,  original);
        }
    }
}
