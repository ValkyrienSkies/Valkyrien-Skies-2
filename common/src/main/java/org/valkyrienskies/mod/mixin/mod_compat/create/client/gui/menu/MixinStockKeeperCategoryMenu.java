package org.valkyrienskies.mod.mixin.mod_compat.create.client.gui.menu;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperCategoryMenu;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.menu.MenuBase;
import net.minecraft.core.Position;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(StockKeeperCategoryMenu.class)
public abstract class MixinStockKeeperCategoryMenu extends MenuBase<StockTickerBlockEntity> {

    private MixinStockKeeperCategoryMenu(MenuType<?> type, int id,
        Inventory inv, FriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    @WrapOperation(
        method = "stillValid",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z")
    )
    private boolean includeShipforDistCheck(Vec3 instance, Position position, double d, Operation<Boolean> original){
        Ship ship = VSGameUtilsKt.getShipManagingPos(contentHolder.getLevel(), contentHolder.getBlockPos());
        if (ship != null) {
            Vector3d newPos = ship.getTransform().getShipToWorld().transformPosition(position.x(), position.y(), position.z(), new Vector3d());
            return original.call(instance, VectorConversionsMCKt.toMinecraft(newPos), d);
        }
        return original.call(instance, position, d);
    }
}
