package org.valkyrienskies.mod.mixin.feature.unloaded_chunk_flight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.PersistentEntitySectionManager.Callback;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Callback.class)
public class MixinPersistentEntitySectionManagerCallback {

    @Shadow
    @Final
    private EntityAccess entity;

    @Shadow
    private long currentSectionKey;

    @Shadow
    private EntitySection<Entity> currentSection;

    @Shadow
    @Final
    PersistentEntitySectionManager field_27271;

    @Shadow
    private void updateStatus(Visibility visibility, Visibility visibility2) {
        throw new IllegalStateException("Mixin failed to apply");
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true, remap = false)
    private void onMoveInclShips(CallbackInfo ci) {
        // Do nothing
        Entity realEntity = (Entity) this.entity;
        BlockPos blockPos = this.entity.blockPosition();
        if (realEntity.level != null && !realEntity.level.isClientSide) {
            ServerLevel slevel = (ServerLevel) realEntity.level;

            EntityDraggingInformation draggingInformation = ((IEntityDraggingInformationProvider) realEntity).getDraggingInformation();

            LoadedServerShip loadedShip = null;

            if (draggingInformation.isEntityBeingDraggedByAShip() && draggingInformation.getLastShipStoodOn() != null) {
                loadedShip = VSGameUtilsKt.getShipObjectWorld(slevel).getLoadedShips().getById(draggingInformation.getLastShipStoodOn());
            } else if (VSGameUtilsKt.getShipMountedTo(realEntity) != null) {
                loadedShip = VSGameUtilsKt.getShipObjectWorld(slevel).getLoadedShips().getById(VSGameUtilsKt.getShipMountedTo(realEntity).getId());
            } else if (VSGameUtilsKt.getShipsIntersecting(slevel, realEntity.getBoundingBox()).iterator().hasNext()) {
                loadedShip = VSGameUtilsKt.getShipObjectWorld(slevel).getLoadedShips().getById(VSGameUtilsKt.getShipsIntersecting(slevel, realEntity.getBoundingBox()).iterator().next().getId());
            }

            if (loadedShip != null) {
                long l = SectionPos.asLong(new BlockPos(VectorConversionsMCKt.toMinecraft(loadedShip.getTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOMLD(blockPos)))));
                if (l != this.currentSectionKey) {
                    Visibility visibility = this.currentSection.getStatus();
                    if (!this.currentSection.remove(realEntity)) {
                        //LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), l);
                    }
                    ((PersistentEntitySectionManagerAccessor) this.field_27271).invokeRemoveSectionIfEmpty(this.currentSectionKey, this.currentSection);
                    EntitySection entitySection = ((PersistentEntitySectionManagerAccessor) this.field_27271).getSectionStorage().getOrCreateSection(l);
                    entitySection.add(realEntity);
                    this.currentSection = entitySection;
                    this.currentSectionKey = l;
                    this.updateStatus(visibility, entitySection.getStatus());
                }
                ci.cancel();
                return;
            }
        }
    }
}
