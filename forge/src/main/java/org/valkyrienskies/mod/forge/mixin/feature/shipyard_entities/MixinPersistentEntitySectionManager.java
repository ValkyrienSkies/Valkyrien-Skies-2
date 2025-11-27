package org.valkyrienskies.mod.forge.mixin.feature.shipyard_entities;

import it.unimi.dsi.fastutil.longs.LongPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntitySectionManager implements OfLevel {
    @Shadow
    @Final
    EntitySectionStorage<Entity> sectionStorage;

    @Unique
    private Level valkyrienskies$level;

    @Override
    public Level getLevel() {
        return valkyrienskies$level;
    }

    @Override
    public void setLevel(final Level level) {
        this.valkyrienskies$level = level;
        ((OfLevel) this.sectionStorage).setLevel(level);
    }

    /**
     * This fixes this function randomly crashing. I'm not sure why but the removeIf() function is buggy
     */
    @ModifyArg(
        method = "processUnloads",
        at = @At(
            target = "Lit/unimi/dsi/fastutil/longs/LongSet;removeIf(Lit/unimi/dsi/fastutil/longs/LongPredicate;)Z",
            value = "INVOKE"
        )
    )
    private LongPredicate processUnloads_catchException(
        final LongPredicate par1
    ) {
        return (l) -> {
            try {
                return par1.test(l);
            } catch (final Exception e) {
                e.printStackTrace();
                return false;
            }
        };
    }
}
