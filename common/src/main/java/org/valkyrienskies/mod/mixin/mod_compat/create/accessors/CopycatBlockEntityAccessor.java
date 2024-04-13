package org.valkyrienskies.mod.mixin.mod_compat.create.accessors;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CopycatBlockEntity.class)
public interface CopycatBlockEntityAccessor {
    @Accessor("consumedItem")
    void setCoItem(ItemStack stack);
}
