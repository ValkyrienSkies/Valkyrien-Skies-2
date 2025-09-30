package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.FlywheelEvents;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.MixinBlockEntityStorageDuck;

@Mixin(value = BlockEntityStorage.class, remap = false)
public abstract class MixinBlockEntityStorage extends Storage<BlockEntity> implements MixinBlockEntityStorageDuck {

    @Unique
    protected WeakHashMap<BlockEntity, ClientShip> vs$BEonShip = new WeakHashMap<>();

    @Unique
    protected WeakHashMap<ClientShip, BlockPos> vs$shipAbsoluteOrigin = new WeakHashMap<>();

    @Unique
    protected WeakHashMap<ClientShip, VisualEmbedding> vs$shipEmbedding = new WeakHashMap<>();

    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        FlywheelEvents.onBlockEntityStorageCreation(this);
    }

    /*
        Adds the BlockEntity to this Storage, and checks if the BlockEntity is on a ship.
        If it is placed on a ship then assign a VisualEmbedding that is bound to the ship and updates per the movement.
        Since ship origin(ShipTransform.PositionInShip/PositionInWorld) will change its position if the ship is modified,
        this class will register the BlockPos of first BlockEntity with visuals placed on the ship as 'absolute origin'.
     */
    @Override
    public void add(VisualizationContext visualizationContext, BlockEntity obj, float partialTick){
        final ClientShip ship = vs$BEonShip.computeIfAbsent(obj, be -> VSGameUtilsKt.getShipObjectManagingPos((ClientLevel) be.getLevel(), be.getBlockPos()));
        if (ship != null) {
            VisualEmbedding embedding = vs$shipEmbedding.get(ship);
            if (embedding == null){
                Matrix3f normalMatrix = new Matrix3f();
                Matrix4f poseMatrix = new Matrix4f();
                embedding = visualizationContext.createEmbedding(obj.getBlockPos());
                poseMatrix.translate(ship.getRenderTransform().getShipToWorld().transformPosition(obj.getBlockPos().getX(), obj.getBlockPos().getY(), obj.getBlockPos().getZ(), new Vector3d()).get(new Vector3f()));
                poseMatrix.rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
                normalMatrix.set(poseMatrix);
                embedding.transforms(poseMatrix, normalMatrix);
                vs$shipEmbedding.put(ship, embedding);
                vs$shipAbsoluteOrigin.put(ship, obj.getBlockPos());
            }
            super.add(embedding, obj, partialTick);
        }
        else super.add(visualizationContext, obj, partialTick);
    }

    /*
        Removes the BlockEntity from this storage. If the BlockEntity was on a ship, then check if the ship is free of any BlockEntities that uses visual.
        If the ship contains no such BlockEntities, then remove the registration of the ship and attached VisualEmbedding too.
     */
    @Inject(
        method = "remove(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        at = @At("HEAD")
    )
    private void preRemove(BlockEntity obj, CallbackInfo ci){
        ClientShip ship = vs$BEonShip.get(obj);
        if (ship != null){
            vs$BEonShip.remove(obj);
            if (!vs$BEonShip.containsValue(ship)){
                vs$shipEmbedding.remove(ship);
                vs$shipAbsoluteOrigin.remove(ship);
            }
        }
    }

    @Inject(
        method = "invalidate",
        at = @At("RETURN")
    )
    private void postInvalidate(CallbackInfo ci){
        vs$BEonShip.clear();
        vs$shipAbsoluteOrigin.clear();
        vs$shipEmbedding.clear();
    }

    /*
        Check the storage and remove every entry that involves the ship.
     */
    @Override
    public void vs$unloadShip(ClientShip ship){
        vs$shipEmbedding.remove(ship);
        vs$shipAbsoluteOrigin.remove(ship);
        for(Entry<BlockEntity, ClientShip> entry : vs$BEonShip.entrySet()){
            if(entry.getValue() == ship) vs$BEonShip.remove(entry.getKey());
            remove(entry.getKey());
        }
    }

    /*
        Updates every VisualEmbedding attached to a ship.
        This should be called manually to update the transformation, or it won't properly update current ship movement.
     */
    @Override
    public void vs$updateAllShips(){
        for(ClientShip ship : vs$shipEmbedding.keySet()){
            Matrix3f normalMatrix = new Matrix3f();
            VisualEmbedding embedding = vs$shipEmbedding.get(ship);
            Matrix4f poseMatrix = new Matrix4f();
            BlockPos anchorPos = vs$shipAbsoluteOrigin.get(ship);
            poseMatrix.translate(ship.getRenderTransform().getShipToWorld().transformPosition(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), new Vector3d()).get(new Vector3f()));
            poseMatrix.rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
            normalMatrix.set(poseMatrix);
            embedding.transforms(poseMatrix, normalMatrix);
        }
    }
}
