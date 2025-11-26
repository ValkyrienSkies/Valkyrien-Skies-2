package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SymmetryMirror.class)
public class MixinSymmetryMirror {
    @Shadow
    protected Vec3 position;

    @Inject(
        method = "writeToNbt",
        at = @At("RETURN")
    )
    private void writeWithDouble(CallbackInfoReturnable<CompoundTag> cir){
        ListTag doubleList = new ListTag();
        doubleList.add(DoubleTag.valueOf(position.x));
        doubleList.add(DoubleTag.valueOf(position.y));
        doubleList.add(DoubleTag.valueOf(position.z));
        cir.getReturnValue().put("vsDoublePos", doubleList);
    }

    @ModifyVariable(
        method = "fromNBT",
        at = @At(value = "STORE"),
        name = "pos"
    )
    private static Vec3 readWithDouble(Vec3 instance, @Local(argsOnly = true) CompoundTag tag){
        final ListTag doubleList = tag.getList("vsDoublePos", 6);
        return new Vec3(doubleList.getDouble(0), doubleList.getDouble(1), doubleList.getDouble(2));
    }
}
