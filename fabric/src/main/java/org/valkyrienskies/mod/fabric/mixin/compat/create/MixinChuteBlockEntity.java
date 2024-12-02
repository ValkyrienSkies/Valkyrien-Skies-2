package org.valkyrienskies.mod.fabric.mixin.compat.create;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixin.mod_compat.create.accessors.ChuteBlockEntityAccessor;

@Mixin(value = ChuteBlockEntity.class)
public class MixinChuteBlockEntity {

    @Inject(method = "findEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V", shift = Shift.AFTER), cancellable = true)
    private void preFindEntities(float itemSpeed, CallbackInfo ci, @Local Vec3 center) {
        ChuteBlockEntity be = ChuteBlockEntity.class.cast(this);

        if (be.getLevel() != null) {

            final ChuteBlockEntityAccessor bea = (ChuteBlockEntityAccessor) be;
            Level level = be.getLevel();

            BlockPos pos = be.getBlockPos();

            AABB searchArea = new AABB(center.add(0, -bea.getBottomPullDistance() - 0.5, 0), center.add(0, -0.5, 0)).inflate(.45f);

            if (VSGameUtilsKt.getShipObjectManagingPos(level, pos) instanceof ServerShip ship) {
                Vector3d searchAreaMin = new Vector3d(searchArea.minX, searchArea.minY, searchArea.minZ);
                Vector3d searchAreaMax = new Vector3d(searchArea.maxX, searchArea.maxY, searchArea.maxZ);

                Vector3d searchAreaReturnMin = new Vector3d();
                Vector3d searchAreaReturnMax = new Vector3d();

                ship.getTransform().getShipToWorld().transformAab(searchAreaMin, searchAreaMax, searchAreaReturnMin, searchAreaReturnMax);

                searchArea = new AABB(searchAreaReturnMin.x, searchAreaReturnMin.y, searchAreaReturnMin.z, searchAreaReturnMax.x, searchAreaReturnMax.y, searchAreaReturnMax.z);

                for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchArea)) {
                    if (!itemEntity.isAlive())
                        continue;
                    ItemStack entityItem = itemEntity.getItem();
                    if (!bea.callCanAcceptItem(entityItem))
                        continue;
                    be.setItem(entityItem.copy(), (float) (itemEntity.getBoundingBox()
                        .getCenter().y - be.getBlockPos().getY()));
                    itemEntity.discard();
                    break;
                }
                ci.cancel();
            }
        }
    }
}
