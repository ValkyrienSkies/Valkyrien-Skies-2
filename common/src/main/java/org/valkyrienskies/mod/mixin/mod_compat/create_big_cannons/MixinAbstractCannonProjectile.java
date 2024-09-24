package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile")
public abstract class MixinAbstractCannonProjectile extends Projectile {
    protected MixinAbstractCannonProjectile(EntityType<? extends Projectile> p_37248_, Level p_37249_) {
        super(p_37248_, p_37249_);
    }

    @WrapOperation(
            method = "shouldFall",
            at = @At(
                    value="INVOKE",
                    target="Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/phys/AABB;)Z"
            )
    )
    private boolean mixinNoCollision(Level level, AABB aabb, Operation<Boolean> original) {
        Stream<Ship> ships = StreamSupport.stream(VSGameUtilsKt.getShipsIntersecting(level, aabb).spliterator(),false);
        return original.call(level, aabb) && ships.allMatch(s -> level.noCollision(toMinecraft(toJOML(aabb).transform(s.getWorldToShip()))));
    }
}
