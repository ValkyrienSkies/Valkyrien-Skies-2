package org.valkyrienskies.mod.mixin.mod_compat.bluemap;

import com.flowpowered.math.vector.Vector2i;
import de.bluecolored.bluemap.core.map.BmMap;
import de.bluecolored.bluemap.core.map.hires.HiresModelManager;
import de.bluecolored.bluemap.core.world.World;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.bluemap.WorldDuck;

@Mixin(BmMap.class)
@Pseudo
public class MixinBmMap {
    @Shadow
    @Final
    private World world;

    @Shadow
    @Final
    private HiresModelManager hiresModelManager;

    @Redirect(
        method = "renderTile",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"
        ),
        remap = false
    )
    boolean skipShipyard(final Predicate<Object> predicate, final Object object) {
        final Vector2i tilePos = (Vector2i) object;
        final var level = ((WorldDuck) world).valkyrienskies$getCorrelatingLevel();
        final var grid = this.hiresModelManager.getTileGrid();
        final var x = grid.getCellMinX(tilePos.getX()) + 1;
        final var z = grid.getCellMinY(tilePos.getY()) + 1;

        final var notShipyard = !VSGameUtilsKt.isBlockInShipyard(level, x, 0, z);

        return predicate.test(tilePos) && notShipyard;
    }
}
