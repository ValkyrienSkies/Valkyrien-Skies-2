package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(PersistentEntitySectionManager.class)
public abstract class MixinPersistentEntitySectionManager implements OfLevel {
    @Shadow
    @Final
    EntitySectionStorage<Entity> sectionStorage;

    @Unique
    private Level level;

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public void setLevel(final Level level) {
        this.level = level;
        ((OfLevel) this.sectionStorage).setLevel(level);
    }

    @Shadow
    @Final
    private LongSet chunksToUnload;

    @Shadow
    @Final
    private Long2ObjectMap<Visibility> chunkVisibility;

    @Shadow
    private boolean processChunkUnload(final long l) {
        throw new IllegalStateException("This should not be invoked");
    }

    /**
     * This fixes this function randomly crashing. I'm not sure why but the removeIf() function is buggy
     */
    @ModifyArg(
			method="processUnloads",
			at=@At(
					target="Lit/unimi/dsi/fastutil/longs/LongSet;removeIf(Lit/unimi/dsi/fastutil/longs/LongPredicate;)Z",
					value="INVOKE"
			)
	)
	private LongPredicate processUnloads_catchException(LongPredicate par1) {
		return (l) -> {
			try {
				return par1.test(l);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		};
	}
}
