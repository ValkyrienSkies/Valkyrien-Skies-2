package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

/*
import dev.engine_room.flywheel.api.visualization.VisualizationManager.RenderDispatcher;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.mixinducks.MixinBlockEntityInstanceManagerDuck;

@Pseudo
@Mixin(value = BlockEntityInstanceManager.class)
@ParametersAreNonnullByDefault
public abstract class MixinBlockEntityInstanceManager extends InstanceManager<BlockEntity> implements
    MixinBlockEntityInstanceManagerDuck {

    @Unique
    private final WeakHashMap<ClientShip, MaterialManager> vs$shipMaterialManagers =
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
                    vs$shipMaterialManagers.computeIfAbsent(ship, k -> vs$createMaterialManager());
                final Vector3i c =
                    ship.getChunkClaim().getCenterBlockCoordinates(VSGameUtilsKt.getYRange(nullableLevel), new Vector3i());
                ((InstancingEngineAccessor) manager).setOriginCoordinate(BlockPos.containing(c.x, c.y, c.z));

                cir.setReturnValue(InstancedRenderRegistry.createInstance(manager, blockEntity));
            }
        }
    }

    @Override
    public WeakHashMap<ClientShip, MaterialManager> vs$getShipMaterialManagers() {
        return vs$shipMaterialManagers;
    }

    @Override
    public void vs$removeShipManager(final ClientShip clientShip) {
        final MaterialManager removed = vs$shipMaterialManagers.remove(clientShip);
        if (removed instanceof final RenderDispatcher removedRenderer) {
            removedRenderer.delete();
        }
    }

    @Unique
    private MaterialManager vs$createMaterialManager() {
        if (Backend.getBackendType() == BackendType.INSTANCING) {
            return InstancingEngine.builder(Contexts.WORLD).build();
        } else if (Backend.getBackendType() == BackendType.BATCHING) {
            return new BatchingEngine();
        } else {
            throw new IllegalArgumentException("Unknown engine type");
        }
    }
}
 */
