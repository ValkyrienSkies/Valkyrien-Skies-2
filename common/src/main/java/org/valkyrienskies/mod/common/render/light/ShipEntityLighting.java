package org.valkyrienskies.mod.common.render.light;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;

public final class ShipEntityLighting {
    private ShipEntityLighting() {
    }

    public static int apply(final int packedLight, final Entity entity, final float partialTick) {
        if (!VSGameConfig.CLIENT.isShipToWorldLightingEnabled()) return packedLight;
        final Vec3 probe = entity.getLightProbePosition(partialTick);
        final var loadedShip = VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), probe.x, probe.y, probe.z);
        final Vector3d world = new Vector3d(probe.x, probe.y, probe.z);
        if (loadedShip instanceof ClientShip ship) ship.getRenderTransform().getShipToWorld().transformPosition(world);
        final boolean useShipAxes = ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM;
        final float light = VsDynamicLight.getShipEmitterList().sampleLight(world.x, world.y, world.z, useShipAxes);
        return withBlockLight(packedLight, light);
    }

    public static int withBlockLight(final int packedLight, final float light) {
        final int block = Math.round(Math.max(0.0f, Math.min(15.0f, light)) * 16.0f);
        // Keep the sky light and retain light from world blocks.
        return (packedLight & 0xffff0000) | Math.max(packedLight & 0xffff, block);
    }
}
