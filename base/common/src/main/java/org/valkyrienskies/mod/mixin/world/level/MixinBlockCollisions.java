package org.valkyrienskies.mod.mixin.world.level;

import net.minecraft.core.Cursor3D;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.util.BugFixUtil;

/**
 * Fix game freezing when a too-large AABB is used in a BlockCollisions object
 */
@Mixin(BlockCollisions.class)
public class MixinBlockCollisions {
    @Shadow
    @Final
    @Mutable
    private AABB box;
    @Shadow
    @Final
    @Mutable
    private Cursor3D cursor;
    @Shadow
    @Final
    @Mutable
    private VoxelShape entityShape;

    @Inject(method = "<init>(Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Z)V", at = @At("RETURN"))
    private void postInit(final CollisionGetter collisionGetter, final Entity entity, final AABB aabb, final boolean bl, final CallbackInfo ci) {
        if (BugFixUtil.INSTANCE.isCollisionBoxToBig(aabb)) {
            final AABB newBox = new AABB(aabb.minX, aabb.minY, aabb.minZ, aabb.minX, aabb.minY, aabb.minZ);
            this.entityShape = Shapes.create(newBox);
            this.box = newBox;
            final int i = Mth.floor(newBox.minX - 1.0E-7) - 1;
            final int j = Mth.floor(newBox.maxX + 1.0E-7) + 1;
            final int k = Mth.floor(newBox.minY - 1.0E-7) - 1;
            final int l = Mth.floor(newBox.maxY + 1.0E-7) + 1;
            final int m = Mth.floor(newBox.minZ - 1.0E-7) - 1;
            final int n = Mth.floor(newBox.maxZ + 1.0E-7) + 1;
            this.cursor = new Cursor3D(i, k, m, j, l, n);
        }
    }
}
