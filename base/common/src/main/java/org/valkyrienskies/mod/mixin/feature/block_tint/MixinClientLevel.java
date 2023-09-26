package org.valkyrienskies.mod.mixin.feature.block_tint;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @ModifyVariable(
        method = "calculateBlockTint(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;)I",
        ordinal = 0,
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockPos fixBlockPos(final BlockPos old) {
        if (!VSGameConfig.CLIENT.getBlockTinting().getFixBlockTinting())
            return old;

        final Vector3d newPos =
            VSGameUtilsKt.toWorldCoordinates(
                ClientLevel.class.cast(this),
                new Vector3d(
                    old.getX(),
                    old.getY(),
                    old.getZ()
                )
            );

        return new BlockPos(
            newPos.x,
            newPos.y,
            newPos.z
        );
    }

    @Inject(
        method = "getBlockTint(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;)I",
        at = @At("HEAD"),
        cancellable = true
    )
    public void getBlockTint(
        final BlockPos blockPos,
        final ColorResolver colorResolver,
        final CallbackInfoReturnable<Integer> cir
    ) {
        if (VSGameConfig.CLIENT.getBlockTinting().getFixBlockTinting() &&
            VSGameUtilsKt.isBlockInShipyard(ClientLevel.class.cast(this), blockPos)
        ) {
            cir.setReturnValue(ClientLevel.class.cast(this)
                .calculateBlockTint(blockPos, colorResolver));
        }
    }


}
