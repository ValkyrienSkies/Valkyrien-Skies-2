package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;
import org.valkyrienskies.mod.mixinducks.world.OfShip;

@Mixin(EntitySectionStorage.class)
public abstract class MixinEntitySectionStorage implements OfLevel {

    @Shadow
    public abstract void forEachAccessibleNonEmptySection(AABB aABB, AbortableIterationConsumer<EntitySection<?>> abortableIterationConsumer);

    @Unique
    private Level level;

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public void setLevel(final Level level) {
        this.level = level;
    }

    @Unique
    private boolean loopingShips = false;

    @Inject(method = "createSection", at = @At("RETURN"))
    void onSectionCreate(final long l, final CallbackInfoReturnable<EntitySection<Entity>> cir) {
        ((OfShip) cir.getReturnValue()).setShip(
            VSGameUtilsKt.getShipManagingPos(level, SectionPos.x(l), SectionPos.z(l))
        );
    }

    @Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"))
    void shipSections(final AABB aABB, final AbortableIterationConsumer<EntitySection<?>> abortableIterationConsumer,
        final CallbackInfo ci) {

        if (level != null && !loopingShips) {
            loopingShips = true;
            VSGameUtilsKt.getShipsIntersecting(level, aABB).forEach(ship -> {
                final var transformedAABB = VectorConversionsMCKt.toMinecraft(
                    VectorConversionsMCKt.toJOML(aABB).transform(ship.getWorldToShip()));

                // No idea how this method works or why it throws (subset bounds are messed up)
                // but let's just catch it for now.

                // java.lang.IllegalArgumentException: Start element (9223367638808264704) is larger than end element (-9223372036854775808)
                try {
                    this.forEachAccessibleNonEmptySection(transformedAABB, abortableIterationConsumer);
                } catch (final IllegalArgumentException ex) {
                    ex.printStackTrace();
                }
            });
            loopingShips = false;
        }
    }
}
