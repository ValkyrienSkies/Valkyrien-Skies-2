package org.valkyrienskies.mod.mixin.feature.unloaded_chunk_flight;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentEntitySectionManager.class)
public interface PersistentEntitySectionManagerAccessor {

    @Accessor
    EntitySectionStorage<Entity> getSectionStorage();

    @Invoker
    void invokeRemoveSectionIfEmpty(long l, EntitySection<Entity> entitySection);
}
