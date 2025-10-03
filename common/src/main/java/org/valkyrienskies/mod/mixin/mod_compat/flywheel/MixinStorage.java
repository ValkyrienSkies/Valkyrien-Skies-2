package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.FlywheelEvents;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.MixinStorageDuck;

@Mixin(value = Storage.class, remap = false)
public abstract class MixinStorage<T> implements MixinStorageDuck<T> {

    @Unique
    protected ConcurrentHashMap<ClientShip, Vec3i> vs$shipAbsoluteOrigin = new ConcurrentHashMap<>();

    @Unique
    protected ConcurrentHashMap<ClientShip, Vec3i> vs$shipRenderOffset = new ConcurrentHashMap<>();

    @Unique
    protected ConcurrentHashMap<ClientShip, VisualEmbedding> vs$shipEmbedding = new ConcurrentHashMap<>();

    @Shadow
    public abstract void remove(T obj);

    @Shadow
    public abstract void update(T obj, float partialTick);

    @Inject(
        method = "invalidate",
        at = @At("HEAD")
    )
    private void preInvalidate(final CallbackInfo ci){
        vs$shipAbsoluteOrigin.clear();
        vs$shipEmbedding.clear();
    }

    @WrapMethod(
        method = "add"
    )
    public void wrapAdd(final VisualizationContext visualizationContext, final T obj, final float partialTick, final Operation<Visual> original){
        final Ship ship;
        final BlockPos pos;
        if (obj instanceof final BlockEntity blockEntity) {
            ship = VSGameUtilsKt.getShipObjectManagingPos(blockEntity.getLevel(), blockEntity.getBlockPos());
            pos = blockEntity.getBlockPos();
        } else if (obj instanceof final Entity entity) {
            ship = VSGameUtilsKt.getShipManaging(entity);
            pos = BlockPos.containing(entity.getPosition(partialTick));
        } else {
            ship = null;
            pos = null;
        }
        if (ship instanceof final ClientShip clientShip) {
            final VisualEmbedding embedding = vs$shipEmbedding.computeIfAbsent(clientShip, s-> {
                final Matrix3f normalMatrix = new Matrix3f();
                final Vec3i offset = visualizationContext.renderOrigin().multiply(-1);
                final Matrix4f poseMatrix = new Matrix4f();
                poseMatrix.translate(s.getRenderTransform().getShipToWorld().transformPosition(pos.getX(), pos.getY(), pos.getZ(), new Vector3d()).get(new Vector3f()));
                poseMatrix.translate(new Vector3f(offset.getX(), offset.getY(), offset.getZ()));
                poseMatrix.rotate(s.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
                normalMatrix.set(poseMatrix);
                final VisualEmbedding result = visualizationContext.createEmbedding(pos);
                result.transforms(poseMatrix, normalMatrix);
                vs$shipAbsoluteOrigin.put(s, pos);
                vs$shipRenderOffset.put(s, offset);
                return result;
            });
            original.call(embedding, obj, partialTick);
            return;
        }
        original.call(visualizationContext, obj, partialTick);
    }

    /*
        Updates every VisualEmbedding attached to a ship.
        This should be called manually to update the transformation, or it won't properly update current ship movement.
     */
    @Override
    public void vs$updateAllShips(){
        for(final ClientShip ship : vs$shipEmbedding.keySet()){
            final Matrix3f normalMatrix = new Matrix3f();
            final VisualEmbedding embedding = vs$shipEmbedding.get(ship);
            final Matrix4f poseMatrix = new Matrix4f();
            final Vec3i anchorPos = vs$shipAbsoluteOrigin.get(ship);
            final Vec3i offset = vs$shipRenderOffset.get(ship);
            poseMatrix.translate(ship.getRenderTransform().getShipToWorld().transformPosition(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), new Vector3d()).get(new Vector3f()));
            poseMatrix.translate(new Vector3f(offset.getX(), offset.getY(), offset.getZ()));
            poseMatrix.rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
            normalMatrix.set(poseMatrix);
            embedding.transforms(poseMatrix, normalMatrix);
        }
    }

    @Override
    public void vs$unloadShip(final ClientShip ship){
        final VisualEmbedding embedding = vs$shipEmbedding.remove(ship);
        if(embedding != null){
            embedding.delete();
        }
        vs$shipAbsoluteOrigin.remove(ship);
    }

    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        FlywheelEvents.onVisualizationManagerCreation(this);
    }
}
