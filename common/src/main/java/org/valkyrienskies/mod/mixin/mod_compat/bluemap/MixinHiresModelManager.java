package org.valkyrienskies.mod.mixin.mod_compat.bluemap;

import com.flowpowered.math.vector.Vector3i;
import de.bluecolored.bluemap.core.map.TileMetaConsumer;
import de.bluecolored.bluemap.core.map.hires.HiresModelManager;
import de.bluecolored.bluemap.core.map.hires.HiresModelRenderer;
import de.bluecolored.bluemap.core.map.hires.HiresTileModel;
import de.bluecolored.bluemap.core.world.World;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.bluemap.WorldDuck;

@Pseudo
@Mixin(HiresModelManager.class)
public class MixinHiresModelManager {

    @Redirect(
        remap = false,
        method = "render",
        at = @At(value = "INVOKE", target = "Lde/bluecolored/bluemap/core/map/hires/HiresModelRenderer;render(Lde/bluecolored/bluemap/core/world/World;Lcom/flowpowered/math/vector/Vector3i;Lcom/flowpowered/math/vector/Vector3i;Lde/bluecolored/bluemap/core/map/hires/HiresTileModel;Lde/bluecolored/bluemap/core/map/TileMetaConsumer;)V")
    )
    void renderModel(final HiresModelRenderer instance, final World world,
        final Vector3i min, final Vector3i max,
        final HiresTileModel model, final TileMetaConsumer tmc
    ) {
        final var aabb = new AABBd(
            min.getX(), min.getY(), min.getZ(),
            max.getX(), max.getY(), max.getZ()
        );

        final var level = ((WorldDuck) world).valkyrienskies$getCorrelatingLevel();
        if (level == null) {
            System.out.println("Valkyrien Skies x BlueMap: Could not find correlating level for bluemap world");
            return;
        }

        //TODO we are begging the gods to not have race conditions here
        final var ships = VSGameUtilsKt.getShipsIntersecting(level, aabb);

        var start = model.size();

        for (final Ship ship : ships) {
            if (!aabb.containsPoint(ship.getTransform().getPositionInWorld())) continue;
            final var shipAABB = ship.getShipAABB();

            assert shipAABB != null;
            final var shipMin = new Vector3i(shipAABB.minX() - 1, shipAABB.minY() - 1, shipAABB.minZ() - 1);
            final var shipMax = new Vector3i(shipAABB.maxX() + 1, shipAABB.maxY() + 1, shipAABB.maxZ() + 1);


            // renders the ship with as origin shipMin.x, 0, shipMin.z
            instance.render(world, shipMin, shipMax, model, tmc);

            final var preTranslation = new Vector3d(
                shipMin.getX(),
                0,
                shipMin.getZ()
            );

            final var postTranslation = new Vector3d(
                -min.getX(),
                0,
                -min.getZ()
            );

            valkyrienskies$transformModel(start, model.size(), model, preTranslation, postTranslation, ship.getTransform().getShipToWorld());

            start = model.size();
        }


        instance.render(world, min, max, model, tmc);
    }

    @Unique
    private void valkyrienskies$transformModel(final int start, final int end, final HiresTileModel model, final Vector3dc preTranslation, final Vector3dc postTranslation, final Matrix4dc transform) {
        final var positions = ((HiresTileModelAccessor) model).getPositions();

        for(int face = start; face < end; ++face) {
            for(int i = 0; i < 3; ++i) {
                final int index = face * 9 + i * 3;
                final double x = positions[index]     + preTranslation.x();
                final double y = positions[index + 1] + preTranslation.y();
                final double z = positions[index + 2] + preTranslation.z();
                positions[index]     = (x * transform.m00()) + (y * transform.m10()) + (z * transform.m20()) + transform.m30() + postTranslation.x();
                positions[index + 1] = (x * transform.m01()) + (y * transform.m11()) + (z * transform.m21()) + transform.m31() + postTranslation.y();
                positions[index + 2] = (x * transform.m02()) + (y * transform.m12()) + (z * transform.m22()) + transform.m32() + postTranslation.z();
            }
        }
    }
}
