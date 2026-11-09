package org.valkyrienskies.mod.mixin.feature.ai.iron_golem;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// sectionsToVillage's distance gradient is per-frame, so a mob in world looking for a
// ship-mounted village (or vice versa) reads zero hits and stays put. @Overwrite (rather
// than @WrapOperation) because one of the three sectionsToVillage call sites is the
// Comparator.comparingInt(level::sectionsToVillage) method reference, whose underlying
// invokevirtual lives in a LambdaMetafactory-generated class and isn't reachable from a
// per-call-site wrap inside this method's bytecode. Algorithm shape is preserved so any
// vanilla caller's semantics are unchanged; mods that also @Overwrite this helper would
// conflict at standard MixinExtras priority resolution.
@Mixin(BehaviorUtils.class)
public abstract class MixinBehaviorUtilsFindVillage {

    /**
     * @author ValkyrienSkies
     * @reason Make findSectionClosestToVillage consider ship-mounted village sections.
     */
    @Overwrite
    public static SectionPos findSectionClosestToVillage(
        final ServerLevel level, final SectionPos seed, final int radius
    ) {
        final int baseDistance = vs$shipAwareSectionsToVillage(level, seed);
        return SectionPos.cube(seed, radius)
            .filter(s -> vs$shipAwareSectionsToVillage(level, s) < baseDistance)
            .min(Comparator.comparingInt(s -> vs$shipAwareSectionsToVillage(level, s)))
            .orElse(seed);
    }

    @Unique
    private static int vs$shipAwareSectionsToVillage(final ServerLevel level, final SectionPos section) {
        int min = level.sectionsToVillage(section);
        if (min == 0) return 0;  // already a village section in world; can't get closer
        final BlockPos center = section.center();
        final double cx = center.getX(), cy = center.getY(), cz = center.getZ();
        // 16 (one section) is enough slop for projection rounding at the section seam.
        final int[] result = new int[]{min};
        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, cx, cy, cz, 16.0, (x, y, z) -> {
            final int d = level.sectionsToVillage(SectionPos.of(BlockPos.containing(x, y, z)));
            if (d < result[0]) result[0] = d;
        });
        return result[0];
    }
}
