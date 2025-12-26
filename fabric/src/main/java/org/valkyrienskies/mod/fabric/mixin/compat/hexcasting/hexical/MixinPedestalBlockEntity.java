package org.valkyrienskies.mod.fabric.mixin.compat.hexcasting.hexical;

//fixme
//import miyucomics.hexical.features.pedestal.PedestalBlockEntity;
//import net.minecraft.core.BlockPos;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.entity.item.ItemEntity;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Pseudo;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//import org.valkyrienskies.core.api.ships.LoadedServerShip;
//import org.valkyrienskies.mod.common.VSGameUtilsKt;
//import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
//
//@Pseudo
//@Mixin(PedestalBlockEntity.class)
//public class MixinPedestalBlockEntity {
//    @Shadow
//    private ItemEntity heldEntity;
//
//    @Inject(method = "configureItemEntity", at = @At("TAIL"), remap = false)
//    private void valkyrienskies$putItemInShipyard(CallbackInfo ci) {
//        BlockPos pos = ((PedestalBlockEntity) ((Object) this)).getBlockPos();
//        if (((PedestalBlockEntity) ((Object) this)).getLevel() instanceof ServerLevel level) {
//            LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
//            if (heldEntity != null && ship != null) {
//                DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(heldEntity, ship);
//            }
//        }
//    }
//
//    @Inject(method = "updateItemEntity", at = @At("TAIL"), remap = false)
//    private void valkyrienskies$makeSureItemIsInShipyard(CallbackInfo ci) {
//        BlockPos pos = ((PedestalBlockEntity) ((Object) this)).getBlockPos();
//        if (((PedestalBlockEntity) ((Object) this)).getLevel() instanceof ServerLevel level) {
//            LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
//            if (heldEntity != null && ship != null) {
//                DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(heldEntity, ship);
//            }
//        }
//    }
//}
