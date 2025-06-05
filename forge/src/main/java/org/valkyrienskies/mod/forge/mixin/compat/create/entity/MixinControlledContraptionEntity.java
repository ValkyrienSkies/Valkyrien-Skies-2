package org.valkyrienskies.mod.forge.mixin.compat.create.entity;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IMixinControlledContraptionEntity;

@Mixin(ControlledContraptionEntity.class)
public abstract class MixinControlledContraptionEntity implements IMixinControlledContraptionEntity {
    @Shadow protected abstract IControlContraption getController();

    //Region start - fix equals -0 != 0
    private Vec3 flatten(Vec3 vec3) {
        if (vec3.x == -0)
            vec3 = new Vec3(0, vec3.y, vec3.z);
        if (vec3.y == -0)
            vec3 = new Vec3(vec3.x, 0, vec3.z);
        if (vec3.z == -0)
            vec3 = new Vec3(vec3.x, vec3.y, 0);
        return vec3;
    }

    @Redirect(method = "shouldActorTrigger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;equals(Ljava/lang/Object;)Z"), remap = false)
    private boolean redirectEquals(Vec3 instance, Object vec3) {
        Vec3 other = (Vec3) vec3;
        other = flatten(other);
        instance = flatten(instance);
        return instance.equals(other);
    }

    //Region end

    @Override
    public IControlContraption grabController(){
        return getController();
    }
}
