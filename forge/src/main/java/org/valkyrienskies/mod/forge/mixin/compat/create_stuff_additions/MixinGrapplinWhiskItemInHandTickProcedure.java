package org.valkyrienskies.mod.forge.mixin.compat.create_stuff_additions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = {"net.mcreator.createstuffadditions.procedures.GrapplinWhiskItemInHandTickProcedure"})
public abstract class MixinGrapplinWhiskItemInHandTickProcedure {
    /**
     * This mixin repeats {@link MixinGrapplinWhiskChainsLineProcedure} but is responsible for game logic, namely players
     * being tethered by the grappling hook around its correct coordinates. We should not break the checks related to
     * blockstates on the grappling hook position. These are already in the shipyard as ensured by raycast mixins of VS,
     * and should not be transformed to world.
     */
    @WrapOperation(
        method = "execute",
        slice = @Slice(
            // getDouble is called 12 times in this method, we need all but the first three. Slicing should be more reliable than spamming ordinals.
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z")
        ),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;getDouble(Ljava/lang/String;)D"),
        require = 0
    )
    private static double replacePosition(CompoundTag instance, String string, Operation<Double> original,
        @Local(argsOnly = true) LevelAccessor world) {
        boolean x = string.equals("xPostion");
        boolean y = string.equals("yPostion");
        boolean z = string.equals("zPostion");
        if (x || y || z) {
            Vector3d truePos = new Vector3d(
                original.call(instance, "xPostion"),
                original.call(instance, "yPostion"),
                original.call(instance, "zPostion")
            );
            Vector3d worldPos = VSGameUtilsKt.getWorldCoordinates((Level)world, BlockPos.containing(truePos.x, truePos.y, truePos.z), truePos);
            if (x) return worldPos.x;
            if (y) return worldPos.y;
            return worldPos.z;
        } else {
            return original.call(instance, string);
        }
    }
}
