package org.valkyrienskies.mod.common.render;

import java.util.List;
import java.util.ServiceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.mod.common.render.Bakery.BakedGeometry;

public interface PlatformBakery {
    PlatformBakery INSTANCE = load();

    static PlatformBakery load() {
        return ServiceLoader.load(PlatformBakery.class, PlatformBakery.class.getClassLoader())
            .findFirst()
            .orElseThrow();
    }

    List<BakedGeometry> bakeBlockQuads(BlockState bs, BlockPos pos);
}
