package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import java.util.function.Supplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel extends Level {
    protected MixinClientLevel(WritableLevelData writableLevelData,
        ResourceKey<Level> resourceKey, RegistryAccess registryAccess,
        Holder<DimensionType> holder,
        Supplier<ProfilerFiller> supplier, boolean bl, boolean bl2, long l,
        int i) {
        super(writableLevelData, resourceKey, registryAccess, holder, supplier, bl, bl2, l, i);
    }

    @Override
    public int getBrightness(LightLayer lightLayer, BlockPos blockPos) {
        int local = super.getBrightness(lightLayer, blockPos);
        if (!VSGameConfig.CLIENT.getDynamicShipLighting()
            && !VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) return local;
        int world = lightLayer.equals(LightLayer.SKY) ? 15 : 0;
        BlockPos worldPos = blockPos;
        if (VSGameUtilsKt.isBlockInShipyard(this, blockPos)) {
            Vec3 center = VSGameUtilsKt.toWorldCoordinates(this, blockPos.getCenter());
            worldPos = BlockPos.containing(center);
            world = super.getBrightness(lightLayer, worldPos);
        }
        if (lightLayer.equals(LightLayer.SKY)) {
            return Math.min(local, world);
        } else {
            int worldToShipCombined = Math.max(local, world);
            if (!VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) {
                return worldToShipCombined;
            } else {
                int shipToWorld = SodiumCompat.getWorldFromShipStorage().getBlockLightAt(worldPos);
                return Math.max(worldToShipCombined, shipToWorld);
            }
        }
    }

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void vs$markBatchedShipSectionDirty(final BlockPos pos, final BlockState oldState,
        final BlockState newState, final int flags, final CallbackInfo ci) {
        final int sectionX = pos.getX() >> 4;
        final int sectionY = pos.getY() >> 4;
        final int sectionZ = pos.getZ() >> 4;
        if (VSGameUtilsKt.getShipManagingPos((ClientLevel) (Object) this, sectionX, sectionZ)
                instanceof final ClientShip ship && ShipRendererKt.getUsesBatchedRenderer(ship)) {
            ShipBatchRenderer.INSTANCE.markSectionDirty(ship.getId(), sectionX, sectionY, sectionZ);
        }
    }
}
