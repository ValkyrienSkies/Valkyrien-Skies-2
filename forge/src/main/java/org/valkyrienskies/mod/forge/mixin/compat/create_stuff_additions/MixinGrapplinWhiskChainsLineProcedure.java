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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = {"net.mcreator.createstuffadditions.procedures.GrapplinWhiskChainsLineProcedure"})
public abstract class MixinGrapplinWhiskChainsLineProcedure {
    /**
     * There's no harm in skipping this check, it shouldn't happen client side anyway.
     */
    @Redirect(
        method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;D)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;isEmptyBlock(Lnet/minecraft/core/BlockPos;)Z"
        ),
        require = 0
    )
    private static boolean skipAnnoyingEmptyBlockCheck(LevelAccessor instance, BlockPos blockPos) {
        return false;
    }

    /**
     * This mixin prevents the mod from crashing on rendering an absurdly long chain. Unfortunately, all instances of
     * "hey what's the position targeted by grappling hook, again?" happen through accessing NBT tags. This looks
     * suboptimal but for an MCreator mod that's a drop in the ocean.
     */
    @WrapOperation(
        method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;D)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/nbt/CompoundTag;getDouble(Ljava/lang/String;)D"
        )
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
