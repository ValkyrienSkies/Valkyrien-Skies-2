package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DeployerHandler.class)
public interface IMixinDeployerHandler {
    @Invoker("shouldActivate")
    static boolean invokeShouldActivate(ItemStack held, Level world, BlockPos targetPos, @Nullable Direction facing){
        throw  new AssertionError();
    }
}
