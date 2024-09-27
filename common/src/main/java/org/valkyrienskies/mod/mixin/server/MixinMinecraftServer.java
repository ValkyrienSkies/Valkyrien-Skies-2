package org.valkyrienskies.mod.mixin.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Unit;
import net.minecraft.BlockUtil;
import net.minecraft.BlockUtil.FoundRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.apigame.GameServer;
import org.valkyrienskies.core.apigame.world.IPlayer;
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore;
import org.valkyrienskies.core.apigame.world.VSPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.VSLevelChunk;
import org.valkyrienskies.mod.common.util.VSServerLevel;
import org.valkyrienskies.mod.common.world.ChunkManagement;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.Weather2Compat;
import org.valkyrienskies.mod.util.KrunchSupport;
import org.valkyrienskies.mod.util.McMathUtilKt;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IShipObjectWorldServerProvider, GameServer {
    @Shadow
    private PlayerList playerList;

    @Shadow
    public abstract ServerLevel overworld();

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Unique
    private ServerShipWorldCore shipWorld;

    @Unique
    private VSPipeline vsPipeline;

    @Unique
    private Set<String> loadedLevels = new HashSet<>();

    @Unique
    private final Map<String, ServerLevel> dimensionToLevelMap = new HashMap<>();

    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"),
        method = "runServer"
    )
    private void beforeInitServer(final CallbackInfo info) {
        ValkyrienSkiesMod.setCurrentServer(MinecraftServer.class.cast(this));
    }

    @Inject(at = @At("TAIL"), method = "stopServer")
    private void afterStopServer(final CallbackInfo ci) {
        ValkyrienSkiesMod.setCurrentServer(null);
    }

    @Nullable
    @Override
    public ServerShipWorldCore getShipObjectWorld() {
        return shipWorld;
    }

    @Nullable
    @Override
    public VSPipeline getVsPipeline() {
        return vsPipeline;
    }

    /**
     * Create the ship world immediately after the levels are created, so that nothing can try to access the ship world
     * before it has been initialized.
     */
    @Inject(
        method = "createLevels",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getDataStorage()Lnet/minecraft/world/level/storage/DimensionDataStorage;"
        )
    )
    private void postCreateLevels(final CallbackInfo ci) {
        // Register blocks
        if (!MassDatapackResolver.INSTANCE.getRegisteredBlocks()) {
            final List<BlockState> blockStateList = new ArrayList<>(Block.BLOCK_STATE_REGISTRY.size());
            Block.BLOCK_STATE_REGISTRY.forEach((blockStateList::add));
            MassDatapackResolver.INSTANCE.registerAllBlockStates(blockStateList);
            ValkyrienSkiesMod.getVsCore().registerBlockStates(MassDatapackResolver.INSTANCE.getBlockStateData());
        }

        // Load ship data from the world storage
        final ShipSavedData shipSavedData = overworld().getDataStorage()
            .computeIfAbsent(ShipSavedData::load, ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);

        // If there was an error deserializing, re-throw it here so that the game actually crashes.
        // We would prefer to crash the game here than allow the player keep playing with everything corrupted.
        final Throwable ex = shipSavedData.getLoadingException();
        if (ex != null) {
            System.err.println("VALKYRIEN SKIES ERROR WHILE LOADING SHIP DATA");
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        // Create ship world and VS Pipeline
        vsPipeline = shipSavedData.getPipeline();

        KrunchSupport.INSTANCE.setKrunchSupported(!vsPipeline.isUsingDummyPhysics());

        shipWorld = vsPipeline.getShipWorld();
        shipWorld.setGameServer(this);

        VSGameEvents.INSTANCE.getRegistriesCompleted().emit(Unit.INSTANCE);

        getShipObjectWorld().addDimension(
            VSGameUtilsKt.getDimensionId(overworld()),
            VSGameUtilsKt.getYRange(overworld()),
            McMathUtilKt.getDEFAULT_WORLD_GRAVITY()
        );
    }

    @Inject(
        method = "tickServer",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        final Set<IPlayer> vsPlayers = playerList.getPlayers().stream()
            .map(VSGameUtilsKt::getPlayerWrapper).collect(Collectors.toSet());

        shipWorld.setPlayers(vsPlayers);

        // region Tell the VS world to load new levels, and unload deleted ones
        final Map<String, ServerLevel> newLoadedLevels = new HashMap<>();
        for (final ServerLevel level : getAllLevels()) {
            final String dimensionId = VSGameUtilsKt.getDimensionId(level);
            newLoadedLevels.put(dimensionId, level);
            dimensionToLevelMap.put(dimensionId, level);
        }
        /*
        for (final var entry : newLoadedLevels.entrySet()) {
            if (!loadedLevels.contains(entry.getKey())) {
                final var yRange = VSGameUtilsKt.getYRange(entry.getValue());
                shipWorld.addDimension(entry.getKey(), yRange);
            }
        }
        */

        for (final String oldLoadedLevelId : loadedLevels) {
            if (!newLoadedLevels.containsKey(oldLoadedLevelId)) {
                shipWorld.removeDimension(oldLoadedLevelId);
                dimensionToLevelMap.remove(oldLoadedLevelId);
            }
        }
        loadedLevels = newLoadedLevels.keySet();
        // endregion

        vsPipeline.preTickGame();
    }

    /**
     * Tick the [shipWorld], then send voxel terrain updates for each level
     */
    @Inject(
        method = "tickChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V",
            shift = Shift.AFTER
        )
    )
    private void preConnectionTick(final CallbackInfo ci) {
        ChunkManagement.tickChunkLoading(shipWorld, MinecraftServer.class.cast(this));
    }

    @Shadow
    public abstract ServerLevel getLevel(ResourceKey<Level> resourceKey);

    @Shadow
    public abstract boolean isNetherEnabled();

    @Inject(
        method = "tickServer",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        vsPipeline.postTickGame();
        // Only drag entities after we have updated the ship positions
        for (final ServerLevel level : getAllLevels()) {
            EntityDragger.INSTANCE.dragEntitiesWithShips(level.getAllEntities());
            if (LoadedMods.getWeather2())
                Weather2Compat.INSTANCE.tick(level);
        }

        //TODO must reimplement
        // handleShipPortals();
    }
/* TODO must redo
    @Unique
    private void handleShipPortals() {
        // Teleport ships that touch portals
        final ArrayList<LoadedServerShip> loadedShipsCopy = new ArrayList<>(shipWorld.getLoadedShips());
        for (final LoadedServerShip shipObject : loadedShipsCopy) {
            if (!ShipSettingsKt.getSettings(shipObject).getChangeDimensionOnTouchPortals()) {
                // Only send ships through portals if it's enabled in settings
                continue;
            }
            final ServerLevel level = dimensionToLevelMap.get(shipObject.getChunkClaimDimension());
            final Vector3dc shipPos = shipObject.getTransform().getPositionInWorld();
            final double bbRadius = 0.5;
            final BlockPos blockPos = BlockPos.containing(shipPos.x() - bbRadius, shipPos.y() - bbRadius, shipPos.z() - bbRadius);
            final BlockPos blockPos2 = BlockPos.containing(shipPos.x() + bbRadius, shipPos.y() + bbRadius, shipPos.z() + bbRadius);
            // Only run this code if the chunks between blockPos and blockPos2 are loaded
            if (level.hasChunksAt(blockPos, blockPos2)) {
                shipObject.decayPortalCoolDown();

                final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                for (int i = blockPos.getX(); i <= blockPos2.getX(); ++i) {
                    for (int j = blockPos.getY(); j <= blockPos2.getY(); ++j) {
                        for (int k = blockPos.getZ(); k <= blockPos2.getZ(); ++k) {
                            mutableBlockPos.set(i, j, k);
                            final BlockState blockState = level.getBlockState(mutableBlockPos);
                            if (blockState.getBlock() == Blocks.NETHER_PORTAL) {
                                // Handle nether portal teleport
                                if (!shipObject.isOnPortalCoolDown()) {
                                    // Move the ship between dimensions
                                    final ServerLevel destLevel = getLevel(level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
                                    // TODO: Do we want portal time?
                                    if (destLevel != null && isNetherEnabled()) { // && this.portalTime++ >= i) {
                                        level.getProfiler().push("portal");
                                        shipChangeDimension(level, destLevel, mutableBlockPos, shipObject);
                                        level.getProfiler().pop();
                                    }
                                }
                                shipObject.handleInsidePortal();
                            } else if (blockState.getBlock() == Blocks.END_PORTAL) {
                                // Handle end portal teleport
                                final ServerLevel destLevel = level.getServer().getLevel(level.dimension() == Level.END ? Level.OVERWORLD : Level.END);
                                if (destLevel == null) {
                                    return;
                                }
                                shipChangeDimension(level, destLevel, null, shipObject);
                            }
                        }
                    }
                }
            }
        }
    }

 */
/* TODO Must redo
    @Unique
    private void shipChangeDimension(@NotNull final ServerLevel srcLevel, @NotNull final ServerLevel destLevel, @Nullable final BlockPos portalEntrancePos, @NotNull final LoadedServerShip shipObject) {
        final PortalInfo portalInfo = findDimensionEntryPoint(srcLevel, destLevel, portalEntrancePos, shipObject.getTransform().getPositionInWorld());
        if (portalInfo == null) {
            // Getting portal info failed? Don't teleport.
            return;
        }
        final ShipTeleportData shipTeleportData = new ShipTeleportDataImpl(
            VectorConversionsMCKt.toJOML(portalInfo.pos),
            shipObject.getTransform().getShipToWorldRotation(),
            new Vector3d(),
            new Vector3d(),
            VSGameUtilsKt.getDimensionId(destLevel),
            null
        );
        shipWorld.teleportShip(shipObject, shipTeleportData);
    }

    @Unique
    @Nullable
    private PortalInfo findDimensionEntryPoint(@NotNull final ServerLevel srcLevel, @NotNull final ServerLevel destLevel, @Nullable final BlockPos portalEntrancePos, @NotNull final Vector3dc shipPos) {
        final boolean bl = srcLevel.dimension() == Level.END && destLevel.dimension() == Level.OVERWORLD;
        final boolean bl2 = destLevel.dimension() == Level.END;
        final Vec3 deltaMovement = Vec3.ZERO;
        final EntityDimensions entityDimensions = new EntityDimensions(1.0f, 1.0f, true);
        if (bl || bl2) {
            final BlockPos blockPos = bl2 ? ServerLevel.END_SPAWN_POINT : destLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destLevel.getSharedSpawnPos());
            return new PortalInfo(new Vec3((double)blockPos.getX() + 0.5, blockPos.getY(), (double)blockPos.getZ() + 0.5), deltaMovement, 0f, 0f);
        }
        final boolean bl3 = destLevel.dimension() == Level.NETHER;
        if (srcLevel.dimension() != Level.NETHER && !bl3) {
            return null;
        }
        final WorldBorder worldBorder = destLevel.getWorldBorder();
        final double d = DimensionType.getTeleportationScale(srcLevel.dimensionType(), destLevel.dimensionType());
        final BlockPos blockPos2 = worldBorder.clampToBounds(shipPos.x() * d, shipPos.y(), shipPos.z() * d);
        return this.getExitPortal(destLevel, blockPos2, bl3, worldBorder).map(foundRectangle -> {
            final Vec3 vec3;
            final Direction.Axis axis;
            if (portalEntrancePos != null) {
                final BlockState blockState = srcLevel.getBlockState(portalEntrancePos);
                if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                    axis = blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
                    final BlockUtil.FoundRectangle foundRectangle2 =
                        BlockUtil.getLargestRectangleAround(portalEntrancePos, axis, 21, Direction.Axis.Y, 21,
                            blockPos -> srcLevel.getBlockState(blockPos) == blockState);
                    vec3 = this.getRelativePortalPosition(axis, foundRectangle2, entityDimensions,
                        VectorConversionsMCKt.toMinecraft(shipPos));
                } else {
                    axis = Direction.Axis.X;
                    vec3 = new Vec3(0.5, 0.0, 0.0);
                }
            } else {
                axis = Direction.Axis.X;
                vec3 = new Vec3(0.5, 0.0, 0.0);
            }
            return PortalShape.createPortalInfo(destLevel, foundRectangle, axis, vec3, entityDimensions, deltaMovement, 0.0f, 0.0f);
        }).orElse(null);
    }

 */

    @Unique
    private Vec3 getRelativePortalPosition(final Direction.Axis axis, final BlockUtil.FoundRectangle foundRectangle, final EntityDimensions entityDimensions, final Vec3 position) {
        return PortalShape.getRelativePosition(foundRectangle, axis, position, entityDimensions);
    }

    @Unique
    private Optional<FoundRectangle> getExitPortal(final ServerLevel serverLevel, final BlockPos blockPos, final boolean bl, final WorldBorder worldBorder) {
        return serverLevel.getPortalForcer().findPortalAround(blockPos, bl, worldBorder);
    }

    @Inject(
        method = "stopServer",
        at = @At("HEAD")
    )
    private void preStopServer(final CallbackInfo ci) {
        if (vsPipeline != null) {
            vsPipeline.setDeleteResources(true);
            vsPipeline.setArePhysicsRunning(true);
        }
        dimensionToLevelMap.clear();
        shipWorld.setGameServer(null);
        shipWorld = null;
    }

    @NotNull
    private ServerLevel getLevelFromDimensionId(@NotNull final String dimensionId) {
        return dimensionToLevelMap.get(dimensionId);
    }

    @Override
    public void moveTerrainAcrossDimensions(
        @NotNull final IShipActiveChunksSet shipChunks,
        @NotNull final String srcDimension,
        @NotNull final String destDimension
    ) {
        final ServerLevel srcLevel = getLevelFromDimensionId(srcDimension);
        final ServerLevel destLevel = getLevelFromDimensionId(destDimension);

        // Copy ship chunks from srcLevel to destLevel
        shipChunks.forEach((final int x, final int z) -> {
            final LevelChunk srcChunk = srcLevel.getChunk(x, z);

            // This is a hack, but it fixes destLevel being in the wrong state
            ((VSServerLevel) destLevel).removeChunk(x, z);

            final LevelChunk destChunk = destLevel.getChunk(x, z);
            ((VSLevelChunk) destChunk).copyChunkFromOtherDimension((VSLevelChunk) srcChunk);
        });

        // Delete ship chunks from srcLevel
        shipChunks.forEach((final int x, final int z) -> {
            final LevelChunk srcChunk = srcLevel.getChunk(x, z);
            ((VSLevelChunk) srcChunk).clearChunk();

            final ChunkPos chunkPos = srcChunk.getPos();
            srcLevel.getChunkSource().updateChunkForced(chunkPos, false);
            ((VSServerLevel) srcLevel).removeChunk(x, z);
        });
    }
}
