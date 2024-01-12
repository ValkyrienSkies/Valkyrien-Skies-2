package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={SchematicToolBase.class})
public abstract class MixinSchematicToolBase {
    @Redirect(
            method = "Lcom/simibubi/create/content/schematics/client/tools/SchematicToolBase;updateTargetPos()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            )
    )
    public Vec3 injectgetLocation(BlockHitResult instance) {
        BlockPos b = instance.getBlockPos();
        return new Vec3(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
    }
}
