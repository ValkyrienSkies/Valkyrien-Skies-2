package org.valkyrienskies.mod.mixin.mod_compat.flywheel_renderer;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {

    @Inject(method = "setBlockEntity", at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private void vs_flywheel$registerInstances(BlockEntity blockEntity, CallbackInfo ci) {
        FlywheelCompat.INSTANCE.addBlockEntity(blockEntity);
    }
}
