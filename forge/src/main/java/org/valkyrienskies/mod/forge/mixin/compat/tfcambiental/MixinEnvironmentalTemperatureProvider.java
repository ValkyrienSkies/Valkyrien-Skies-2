package org.valkyrienskies.mod.forge.mixin.compat.tfcambiental;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "com.lumintorious.tfcambiental.api.EnvironmentalTemperatureProvider")
public interface MixinEnvironmentalTemperatureProvider {

    // Use an Overwrite because mixin in forge doesn't support injecting into interfaces (?!)
    @Overwrite(remap = false)
    static boolean calculateEnclosure(final Player player, final int radius) {
        // VS: Use player.blockPosition() instead of getOnPos() if getOnPos() is in a ship.
        BlockPos pos = player.getOnPos();
        if (VSGameUtilsKt.isBlockInShipyard(player.level, pos)) {
            pos = player.blockPosition();
        }

        // Original method
        final PathNavigationRegion
            region = new PathNavigationRegion(player.level, pos.above().offset(-radius, -radius, -radius),
            pos.above().offset(radius, 400, radius));
        final Bee guineaPig = new Bee(EntityType.BEE, player.level);
        guineaPig.setPos(player.getPosition(0.0F));
        guineaPig.setBaby(true);
        final FlyNodeEvaluator evaluator = new FlyNodeEvaluator();
        final PathFinder finder = new PathFinder(evaluator, 500);
        final Path path = finder.findPath(region, guineaPig, Set.of(pos.above().atY(258)), 500.0F, 0, 12.0F);
        final boolean isIndoors = path == null || path.getNodeCount() < 255 - pos.above().getY();
        return isIndoors;
    }

}
