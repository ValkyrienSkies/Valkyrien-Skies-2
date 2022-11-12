package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.util.TriConsumer;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.world.OfLevel;

@Mixin(ServerLevel.class)
public class MixinServerLevel {

    @Shadow
    @Final
    private PersistentEntitySectionManager<Entity> entityManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    void configureEntitySections(final CallbackInfo ci) {
        ((OfLevel) entityManager).setLevel(ServerLevel.class.cast(this));
    }

    /**
     * Gets called for each type of getEntities of Level It will check if the aabb is to big and return nothing if it is
     * It will also include the ship-space entities in the list
     *
     * @author ewoudje
     */
    @Unique
    public <T extends Entity> void getEntitiesInShip(
        final AABB area,
        final List<T> list,
        final TriConsumer<LevelChunk, AABB, List<T>> getter) {

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
