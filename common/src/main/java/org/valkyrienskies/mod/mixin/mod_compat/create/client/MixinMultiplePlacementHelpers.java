package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.foundation.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(targets = {
        "com.simibubi.create.content.contraptions.bearing.SailBlock$PlacementHelper",
        "com.simibubi.create.foundation.placement.PoleHelper",
        "com.simibubi.create.content.decoration.girder.GirderPlacementHelper",
        "com.simibubi.create.content.trains.display.FlapDisplayBlock$PlacementHelper"
})
public class MixinMultiplePlacementHelpers {

    @Unique
    private Level world;

    @Inject(method = "getOffset", at = @At("HEAD"), remap = false)
    private void harvestWorld(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray, CallbackInfoReturnable<PlacementOffset> cir) {
        this.world = world;
    }

    @Redirect(method = "getOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 redirectGetLocation(BlockHitResult instance) {
        Vec3 result = instance.getLocation();
        Ship ship = VSGameUtilsKt.getShipManagingPos(world, instance.getBlockPos());
        if (ship != null && !VSGameUtilsKt.isBlockInShipyard(world,result.x,result.y,result.z)) {
            Vector3d tempVec = VectorConversionsMCKt.toJOML(result);
            ship.getWorldToShip().transformPosition(tempVec, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }
}
