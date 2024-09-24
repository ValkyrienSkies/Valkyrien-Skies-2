package org.valkyrienskies.mod.mixin.mod_compat.create.accessors;

import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChuteBlockEntity.class)
public interface ChuteBlockEntityAccessor {
    @Accessor("bottomPullDistance")
    float getBottomPullDistance();

    @Invoker
    boolean callCanAcceptItem(ItemStack item);
}
