package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.jozufozu.flywheel.api.MaterialManager;
import com.jozufozu.flywheel.api.instance.Instance;
import com.jozufozu.flywheel.backend.Backend;
import com.jozufozu.flywheel.backend.instancing.InstanceManager;
import com.jozufozu.flywheel.backend.instancing.InstancedRenderRegistry;
import com.jozufozu.flywheel.backend.instancing.batching.BatchingEngine;
import com.jozufozu.flywheel.backend.instancing.blockentity.BlockEntityInstanceManager;
import com.jozufozu.flywheel.backend.instancing.instancing.InstancingEngine;
import com.jozufozu.flywheel.config.BackendType;
import com.jozufozu.flywheel.core.Contexts;
import java.util.WeakHashMap;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.world.LevelYRange;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.MixinBlockEntityInstanceManagerDuck;

@Pseudo
@Mixin(value = BlockEntityInstanceManager.class)
@ParametersAreNonnullByDefault
public abstract class MixinBlockEntityInstanceManager extends InstanceManager<BlockEntity> implements
    MixinBlockEntityInstanceManagerDuck {

    public WeakHashMap<ClientShip, MaterialManager> getShipMaterialManagers() {
        return shipMaterialManagers;
    }

    @Unique
    private final WeakHashMap<ClientShip, MaterialManager> shipMaterialManagers =
        new WeakHashMap<>();

    public MixinBlockEntityInstanceManager(final MaterialManager materialManager) {
        super(materialManager);
    }

    @Inject(
        method = "createRaw(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lcom/jozufozu/flywheel/backend/instancing/AbstractInstance;",
        at = @At("HEAD"),
        cancellable = true
    )
    void preCreateRaw(final BlockEntity blockEntity, final CallbackInfoReturnable<Instance> cir) {
        final Level nullableLevel = blockEntity.getLevel();
        if (nullableLevel instanceof final ClientLevel level) {
            final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(
                level, blockEntity.getBlockPos());
            if (ship != null) {
                final MaterialManager manager =
                    shipMaterialManagers.computeIfAbsent(ship, k -> createMaterialManager());
                final Vector3i c =
                    ship.getChunkClaim().getCenterBlockCoordinates(new LevelYRange(0, 0), new Vector3i());
                ((InstancingEngineAccessor) manager).setOriginCoordinate(new BlockPos(c.x, c.y, c.z));

                cir.setReturnValue(InstancedRenderRegistry.createInstance(manager, blockEntity));
            }
        }
    }

    @Unique
    private MaterialManager createMaterialManager() {
        if (Backend.getBackendType() == BackendType.INSTANCING) {
            return InstancingEngine.builder(Contexts.WORLD).build();
        } else if (Backend.getBackendType() == BackendType.BATCHING) {
            return new BatchingEngine();
        } else {
            throw new IllegalArgumentException("Unknown engine type");
        }
    }
}
