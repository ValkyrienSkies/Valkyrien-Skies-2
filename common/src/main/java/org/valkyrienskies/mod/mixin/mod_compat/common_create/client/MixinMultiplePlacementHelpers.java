package org.valkyrienskies.mod.mixin.mod_compat.common_create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.CompatUtil;

@Pseudo
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
    private void harvestWorld(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray,
        @Coerce CallbackInfoReturnable<Object> cir) {
        this.world = world;
    }

    @WrapOperation(method = "getOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetLocation(BlockHitResult instance, Operation<Vec3> original) {
        return CompatUtil.INSTANCE.toSameSpaceAs(world, original.call(instance), instance.getBlockPos());
    }
}
