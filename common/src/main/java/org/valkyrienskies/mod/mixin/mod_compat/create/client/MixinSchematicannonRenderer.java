package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(SchematicannonRenderer.class)
public abstract class MixinSchematicannonRenderer {

    // This is somehow broken on forge?? So we need a remap=false (for forge) AND a remap=true (for fabric) variant
    @ModifyExpressionValue(
        method = "getCannonAngles",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity;previousTarget:Lnet/minecraft/core/BlockPos;"
        ),
        require = 0,
        remap = false
    )
    private static BlockPos transformPreviousTargetForge(
        BlockPos original,
        @Local(argsOnly = true) SchematicannonBlockEntity blockEntity, @Local(argsOnly = true) BlockPos blockPos
    ) {
        return original != null ? BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(blockEntity.getLevel(), original.getCenter(), blockPos)
        ) : null;
    }

    @ModifyExpressionValue(
        method = "getCannonAngles",
        at = @At(
            value = "FIELD",
            target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity;previousTarget:Lnet/minecraft/core/BlockPos;"
        ),
        require = 0
    )
    private static BlockPos transformPreviousTargetFabric(
        BlockPos original,
        @Local(argsOnly = true) SchematicannonBlockEntity blockEntity, @Local(argsOnly = true) BlockPos blockPos
    ) {
        return original != null ? BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(blockEntity.getLevel(), original.getCenter(), blockPos)
        ) : null;
    }

    @Inject(
        method = "getCannonAngles",
        at = @At(
            // After the target BlockPos is retrieved. Guaranteed to be non-null
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;subtract(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;"
        )
    )
    private static void transformBlockPos(
        SchematicannonBlockEntity blockEntity, BlockPos pos, float partialTicks, CallbackInfoReturnable<double[]> cir,
        @Local(ordinal = 1) LocalRef<BlockPos> target
    ) {
        target.set(BlockPos.containing(
            CompatUtil.INSTANCE.toSameSpaceAs(blockEntity.getLevel(), target.get().getCenter(), pos)
        ));
    }

    @WrapOperation(
        method = "renderLaunchedBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 1
        )
    )
    private static Vec3 transformTarget(
        Vec3i vec3i, Operation<Vec3> original, @Local(argsOnly = true) SchematicannonBlockEntity blockEntity
    ) {
        return CompatUtil.INSTANCE.toSameSpaceAs(
            blockEntity.getLevel(), original.call(vec3i), blockEntity.getBlockPos()
        );
    }
}
