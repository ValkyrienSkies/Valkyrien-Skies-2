package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SchematicToolBase is responsible for the placement position of the schematic.
 * <p>
 * Create uses HitResult::getLocation to get the schematic placement position, which doesn't respect ship-space.
 * This mixin redirects it to BlockHitResult::getBlockPos instead which *does* respect ship-space.
 * The original behaviour is otherwise not changed.
 */
@Mixin(value={SchematicToolBase.class})
public abstract class MixinSchematicToolBase {
    @Redirect(
            method = "updateTargetPos()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/BlockHitResult;getLocation()Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            )
    )
    public Vec3 redirectGetLocation(BlockHitResult instance) {
        BlockPos b = instance.getBlockPos();
        return Vec3.atLowerCornerOf(b);
    }
}
