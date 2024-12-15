package org.valkyrienskies.mod.mixin.mod_compat.reachentityattributes;

import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(ReachEntityAttributes.class)
public class MixinReachEntityAttributes {
    @Shadow
    public static double getReachDistance(final LivingEntity entity, final double baseReachDistance) {
        return 0.0;
    }

    /**
     * @author Triode
     * @reason Fix getting players within reach of ship blocks
     */
    @Overwrite
    public static List<Player> getPlayersWithinReach(final Predicate<Player> viewerPredicate, final Level world, final int x, final int y, final int z, final double baseReachDistance) {
        final List<Player> playersWithinReach = new ArrayList<>(0);
        for (final Player player : world.players()) {
            if (viewerPredicate.test(player)) {
                final var reach = getReachDistance(player, baseReachDistance);
                Vec3 eye = player.getEyePosition();
                if (player instanceof IEntityDraggingInformationProvider dragProvider && dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                    if (dragProvider.getDraggingInformation().getServerRelativePlayerPosition() != null) {
                        eye = VectorConversionsMCKt.toMinecraft(dragProvider.getDraggingInformation().getServerRelativePlayerPosition());
                    }
                }
                if (VSGameUtilsKt.squaredDistanceBetweenInclShips(world, x + 0.5, y + 0.5, z + 0.5, eye.x, eye.y, eye.z) <= (reach * reach)) {
                    playersWithinReach.add(player);
                }
            }
        }
        return playersWithinReach;
    }
}
