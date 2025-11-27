package org.valkyrienskies.mod.mixin.mod_compat.etf;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(value = BlockEntity.class, priority = 1200)
public abstract class MixinBlockEntity implements ETFEntity {
    @Override
    public float etf$distanceTo(final Entity entity) {
        final var level = Minecraft.getInstance().level;
        final var aW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(etf$getBlockPos()));
        final var bW = VSGameUtilsKt.toWorldCoordinates(level, entity.position());
        final var dist = aW.distanceTo(bW);
        return (float) dist;
    }
}
