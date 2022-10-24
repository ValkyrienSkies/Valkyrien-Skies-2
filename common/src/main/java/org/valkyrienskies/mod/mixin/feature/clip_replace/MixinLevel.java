package org.valkyrienskies.mod.mixin.feature.clip_replace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Level.class)
public abstract class MixinLevel implements BlockGetter {

    @Shadow
    @Final
    protected static Logger LOGGER;

    @Override
    public BlockHitResult clip(final ClipContext clipContext) {

        if (VSGameUtilsKt.getShipManagingPos(Level.class.cast(this), clipContext.getTo()) !=
            VSGameUtilsKt.getShipManagingPos(Level.class.cast(this), clipContext.getFrom())) {
            LOGGER.warn("Trying to clip from " +
                clipContext.getFrom() + " to " + clipContext.getTo() +
                " wich one of them is in a shipyard wich is ... sus!!");

            final Vec3 vec3 = clipContext.getFrom().subtract(clipContext.getTo());
            return BlockHitResult.miss(
                clipContext.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z),
                new BlockPos(clipContext.getTo())
            );
        } else {
            return RaycastUtilsKt.clipIncludeShips(Level.class.cast(this), clipContext);
        }
    }
}
