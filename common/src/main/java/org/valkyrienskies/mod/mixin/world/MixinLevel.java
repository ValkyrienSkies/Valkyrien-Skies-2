package org.valkyrienskies.mod.mixin.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectServer;
import org.valkyrienskies.core.game.ships.ShipObjectWorld;
import org.valkyrienskies.mod.api.ShipBlockEntity;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Level.class)
public class MixinLevel {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger();

    @Shadow
    @Final
    public boolean isClientSide;

    @Inject(method = "setBlockEntity", at = @At("HEAD"))
    public void onSetBlockEntity(final BlockPos blockPos, final BlockEntity blockEntity, final CallbackInfo ci) {
        if (!this.isClientSide) {
            final ShipObjectServer obj = VSGameUtilsKt.getShipObjectManagingPos(ServerLevel.class.cast(this), blockPos);
            if (obj != null && blockEntity instanceof ShipBlockEntity) {
                ((ShipBlockEntity) blockEntity).setShip(obj);
            }
        }
    }

    // region getEntities Hell (see #getEntitiesInShip)
    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB1(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getEntities(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB2(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getEntitiesOfClass",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB3(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @ModifyVariable(
        method = "getLoadedEntitiesOfClass",
        at = @At("HEAD"),
        argsOnly = true
    )
    public AABB moveAABB4(final AABB aabb) {
        return VSGameUtilsKt.transformAabbToWorld(Level.class.cast(this), aabb);
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/Level;getChunkSource()Lnet/minecraft/world/level/chunk/ChunkSource;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true)
    public void getEntitiesInShip1(@Nullable final Entity entity,
        final AABB area,
        @Nullable final Predicate<? super Entity> predicate,
        final CallbackInfoReturnable<List<Entity>> cir,
        final List<Entity> list) {
        getEntitiesInShip(area, list, cir,
            (a, b, c) -> a.getEntities(entity, b, c, predicate));
    }

    @Inject(
        method = "getLoadedEntitiesOfClass",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/Level;getChunkSource()Lnet/minecraft/world/level/chunk/ChunkSource;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true)
    public <T extends Entity> void getEntitiesInShip2(final Class<? extends T> clazz, final AABB area,
        @Nullable final Predicate<? super T> predicate,
        final CallbackInfoReturnable<List<T>> cir,
        final int i1,
        final int i2,
        final int i3,
        final int i4,
        final List<T> list) {
        getEntitiesInShip(area, list, cir, (a, b, c) -> a.getEntitiesOfClass(clazz, b, c, predicate));
    }

    @Inject(
        method = "getEntitiesOfClass",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/Level;getChunkSource()Lnet/minecraft/world/level/chunk/ChunkSource;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true)
    public <T extends Entity> void getEntitiesInShip3(
        final Class<? extends T> clazz,
        final AABB area,
        @Nullable final Predicate<? super T> predicate,
        final CallbackInfoReturnable<List<T>> cir,
        final int i1,
        final int i2,
        final int i3,
        final int i4,
        final List<T> list) {
        getEntitiesInShip(area, list, cir, (a, b, c) -> a.getEntitiesOfClass(clazz, b, c, predicate));
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/Level;getChunkSource()Lnet/minecraft/world/level/chunk/ChunkSource;",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true)
    public <T extends Entity> void getEntitiesInShip4(@Nullable final EntityType<T> entityType,
        final AABB area,
        final Predicate<? super T> predicate,
        final CallbackInfoReturnable<List<T>> cir,
        final int i1,
        final int i2,
        final int i3,
        final int i4,
        final List<T> list) {
        getEntitiesInShip(area, list, cir, (a, b, c) -> a.getEntities(entityType, b, c, predicate));
    }
    //endregion

    @Unique
    public boolean isCollisionBoxToBig(final AABB aabb) {
        return aabb.getXsize() > 1000 || aabb.getYsize() > 1000 || aabb.getZsize() > 1000;
    }

    /**
     * @author ewoudje
     * <p>
     * Gets called for each type of getEntities of Level It will check if the aabb is to big and return nothing if it is
     * It will also include the ship-space entities in the list
     */
    @Unique
    public <T extends Entity> void getEntitiesInShip(
        final AABB area,
        final List<T> list,
        final CallbackInfoReturnable<List<T>> cir,
        final TriConsumer<LevelChunk, AABB, List<T>> getter) {

        if (isCollisionBoxToBig(area)) {
            LOGGER.error("Collision box is too big! " + area + " returning empty list! this might break things");
            cir.setReturnValue(list);
            cir.cancel();
            return;
        }
        final ChunkSource chunkSource = Level.class.cast(this).getChunkSource();

        final AABBdc original = VectorConversionsMCKt.toJOML(area);
        final AABBd transformed = new AABBd();

        // Gets accessed before initialization
        final ShipObjectWorld world = IShipObjectWorldProvider.class.cast(this).getShipObjectWorld();
        if (world == null) {
            return;
        }

        world.getShipObjectsIntersecting(original).forEach((Object shipT) -> {
            final ShipObject ship = (ShipObject) shipT;
            original.transform(ship.getWorldToShip(), transformed);

            final int i = Mth.floor((transformed.minX - 2.0) / 16.0);
            final int j = Mth.ceil((transformed.maxX + 2.0) / 16.0);
            final int k = Mth.floor((transformed.minZ - 2.0) / 16.0);
            final int l = Mth.ceil((transformed.maxZ + 2.0) / 16.0);

            for (int m = i; m < j; ++m) {
                for (int n = k; n < l; ++n) {
                    final LevelChunk levelChunk = chunkSource.getChunkNow(m, n);
                    if (levelChunk == null) {
                        continue;
                    }

                    getter.accept(levelChunk, VectorConversionsMCKt.toMinecraft(transformed), list);
                }
            }
        });
    }
}
