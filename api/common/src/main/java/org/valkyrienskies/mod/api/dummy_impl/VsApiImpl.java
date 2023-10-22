package org.valkyrienskies.mod.api.dummy_impl;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.event.ListenableEvent;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.VsApi;
import org.valkyrienskies.mod.api.events.PostRenderShipEvent;
import org.valkyrienskies.mod.api.events.PreRenderShipEvent;
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent;

class VsApiImpl implements VsApi {
    @NotNull
    @Override
    public ListenableEvent<RegisterBlockStateEvent> getRegisterBlockStateEvent() {
        return new ListenableEventImpl<>();
    }

    @NotNull
    @Override
    public ListenableEvent<PreRenderShipEvent> getPreRenderShipEvent() {
        return new ListenableEventImpl<>();
    }

    @NotNull
    @Override
    public ListenableEvent<PostRenderShipEvent> getPostRenderShipEvent() {
        return new ListenableEventImpl<>();
    }

    @Override
    public boolean isShipMountingEntity(@NotNull final Entity entity) {
        return false;
    }

    @NotNull
    @Override
    public Screen createConfigScreenLegacy(@NotNull final Screen parent, final Class<?> @NotNull ... configs) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Ship getShipManagingBlock(@Nullable final Level level, @Nullable final BlockPos pos) {
        return null;
    }

    @Nullable
    @Override
    public Ship getShipManagingChunk(@Nullable final Level level, @Nullable final ChunkPos pos) {
        return null;
    }
}
