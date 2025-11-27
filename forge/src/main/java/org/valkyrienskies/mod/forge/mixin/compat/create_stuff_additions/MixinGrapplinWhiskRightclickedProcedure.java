package org.valkyrienskies.mod.forge.mixin.compat.create_stuff_additions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {"net.mcreator.createstuffadditions.procedures.GrapplinWhiskRightclickedProcedure"}, remap = false)
public abstract class MixinGrapplinWhiskRightclickedProcedure {
    /**
     * For some reason ship-aware view vectors clash with raycasting in this mod, so when a player is standing on a ship
     * the raycast goes into a completely different direction. Replacing it with original-ish MC behavior.
     */
    @WrapOperation(
        method = "execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getViewVector(F)Lnet/minecraft/world/phys/Vec3;"
        ),
        require = 0
    )
    private static Vec3 dumbViewVector(Entity instance, float scale, Operation<Vec3> original) {
        float f = instance.getViewXRot(scale);
        float g = instance.getViewYRot(scale);
        // Original MC code.
        float h = f * ((float)Math.PI / 180);
        float i = -g * ((float)Math.PI / 180);
        float j = Mth.cos(i);
        float k = Mth.sin(i);
        float l = Mth.cos(h);
        float m = Mth.sin(h);
        return new Vec3(k * l, -m, j * l);
    }
}
