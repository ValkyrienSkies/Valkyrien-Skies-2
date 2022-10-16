package org.valkyrienskies.mod.mixin.feature.clip_replace;

import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(BlockGetter.class)
public interface MixinBlockGetter {

    @Redirect(method = "clip",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;traverseBlocks(Lnet/minecraft/world/level/ClipContext;Ljava/util/function/BiFunction;Ljava/util/function/Function;)Ljava/lang/Object;"))
    default Object clip(final ClipContext clipContext, final BiFunction<ClipContext, BlockPos, Object> biFunction,
        final Function<ClipContext, Object> function) {

        return RaycastUtilsKt.clipIncludeShips(Level.class.cast(this), clipContext);
    }

}
