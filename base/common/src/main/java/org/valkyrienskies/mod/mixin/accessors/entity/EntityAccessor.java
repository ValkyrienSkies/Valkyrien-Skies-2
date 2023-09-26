package org.valkyrienskies.mod.mixin.accessors.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("position")
    void setPosNoUpdates(Vec3 pos);

    @Accessor("blockPosition")
    void setBlockPosition(BlockPos blockPosition);

    @Accessor("blockPosition")
    BlockPos getBlockPosition();

    @Accessor("feetBlockState")
    void setFeetBlockState(BlockState feetBlockState);

    @Accessor("portalCooldown")
    void setPortalCooldown(int portalCooldown);

    @Accessor("portalCooldown")
    int getPortalCooldown();

    @Accessor("portalEntrancePos")
    BlockPos getPortalEntrancePos();
}
