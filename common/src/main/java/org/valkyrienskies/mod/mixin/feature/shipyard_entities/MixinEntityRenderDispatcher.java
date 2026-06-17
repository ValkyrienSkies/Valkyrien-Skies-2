package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = EntityRenderDispatcher.class, priority = 500)
public class MixinEntityRenderDispatcher {

    @Shadow
    public Camera camera;

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final Entity entity, final CallbackInfoReturnable<Double> cir) {
        final Vec3 pos = entity.position();
        // entity seems to be null sometimes when the "real camera" mod is used
        if (entity == null) return;
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceToInclShips(entity, pos.x, pos.y, pos.z));
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final double x, final double y, final double z,
        final CallbackInfoReturnable<Double> cir) {
        // entity seems to be null sometimes when the "real camera" mod is used
        if (camera.getEntity() == null) return;
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceToInclShips(camera.getEntity(), x, y, z));
    }

    @Inject(method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE),
        locals = LocalCapture.CAPTURE_FAILHARD)
    <T extends Entity> void render(
        final T entity, final double x, final double y, final double z, final float rotationYaw,
        final float partialTicks, final PoseStack matrixStack,
        final MultiBufferSource buffer, final int packedLight, final CallbackInfo ci,
        final EntityRenderer<T> entityRenderer
    ) {
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTicks);

        if (shipMountedToData != null) {
            // Remove the earlier applied translation
            matrixStack.popPose();
            matrixStack.pushPose();

            final ShipTransform renderTransform = ((ClientShip) shipMountedToData.getShipMountedTo()).getRenderTransform();

            final Vec3 entityPosition = entity.getPosition(partialTicks);
            final Vector3dc transformed = renderTransform.getShipToWorld().transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());

            final double camX = x - entityPosition.x;
            final double camY = y - entityPosition.y;
            final double camZ = z - entityPosition.z;

            final Vec3 offset = entityRenderer.getRenderOffset(entity, partialTicks);
            final Vector3dc scale = renderTransform.getShipToWorldScaling();

            matrixStack.translate(transformed.x() + camX, transformed.y() + camY, transformed.z() + camZ);
            matrixStack.mulPose(VS$ROTATION.set(renderTransform.getShipToWorldRotation()));
            matrixStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
            matrixStack.translate(offset.x, offset.y, offset.z);
        } else {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                // Remove the earlier applied translation
                matrixStack.popPose();
                matrixStack.pushPose();

                VSEntityManager.INSTANCE.getHandler(entity)
                    .applyRenderTransform(ship, entity, entityRenderer, x, y, z,
                        rotationYaw, partialTicks, matrixStack,
                        buffer, packedLight);
            } else if (entity.isPassenger()) {
                final ClientShip vehicleShip =
                    (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(),
                        entity.getVehicle().blockPosition());
                // If the entity is a passenger and that vehicle is in ship space
                if (vehicleShip != null) {
                    VSEntityManager.INSTANCE.getHandler(entity.getVehicle())
                        .applyRenderOnMountedEntity(vehicleShip, entity.getVehicle(), entity, partialTicks,
                            matrixStack);
                }
            }
        }
    }

    @ModifyReturnValue(
        method = "shouldRender",
        at = @At("RETURN")
    )
    boolean shouldRender(final boolean returns, final Entity entity, final Frustum frustum,
        final double camX, final double camY, final double camZ) {

        if (!returns) {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                AABB aABB = entity.getBoundingBoxForCulling().inflate(0.5);
                if (aABB.hasNaN() || aABB.getSize() == 0.0) {
                    aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0,
                        entity.getZ() - 2.0, entity.getX() + 2.0,
                        entity.getY() + 2.0, entity.getZ() + 2.0);
                }
                final AABBd aabb = VectorConversionsMCKt.toJOML(aABB);

                // Get the in world position and do it minus what the aabb already has and then add the offset
                aabb.transform(ship.getRenderTransform().getShipToWorld());
                return frustum.isVisible(VectorConversionsMCKt.toMinecraft(aabb));
            }
        }

        return returns;
    }

    // Vanilla renderShadow scans world cells under the entity for the shadow surface; for a ship-mounted entity those world cells are air. Re-emit the vertices over the ship's cells: top of each shipyard column (shadow 1) tiles across the deck via the full ship rotation; subsequent cells (shadow 2+) are forced to render at world-down positions from shadow 1 (cast direction = world-Y, gravity-aligned), with cell content drawn from whichever ship cell the world-down ray catches. Per-step blockWeight is uniform across columns (vanilla parity for per-Y-level alpha).
    @Unique
    private static final RenderType VS$SHADOW_RENDER_TYPE =
        RenderType.entityShadow(new ResourceLocation("textures/misc/shadow.png"));

    // Render runs on the client thread only — static scratches over ThreadLocal.
    @Unique private static final Quaternionf VS$ROTATION = new Quaternionf();
    @Unique private static final Vector3d VS$ENTITY_SHIP = new Vector3d();
    @Unique private static final Vector3d VS$FIRST_WORLD = new Vector3d();
    @Unique private static final Vector3d VS$SHIP_LOOKUP = new Vector3d();
    @Unique private static final Vector3f VS$VERTEX = new Vector3f();

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void vs$customShipShadow(
        final PoseStack poseStack, final MultiBufferSource buffer, final Entity entity,
        final float weight, final float partialTicks, final LevelReader level, final float radius,
        final CallbackInfo ci
    ) {
        final Ship ship = VSGameUtilsKt.getEnclosingShip(entity);
        if (ship == null) return;

        final ShipTransform transform = ((ClientShip) ship).getRenderTransform();
        final Matrix4dc shipToWorld = transform.getShipToWorld();
        final Matrix4dc worldToShip = transform.getWorldToShip();

        final double wx = Mth.lerp((double) partialTicks, entity.xOld, entity.getX());
        final double wy = Mth.lerp((double) partialTicks, entity.yOld, entity.getY());
        final double wz = Mth.lerp((double) partialTicks, entity.zOld, entity.getZ());

        final Vector3d entityShip = worldToShip.transformPosition(wx, wy, wz, VS$ENTITY_SHIP);

        float r = radius;
        if (entity instanceof Mob mob && mob.isBaby()) {
            r *= 0.5f;
        }
        final float h = Math.min(weight / 0.5f, r);

        final int xMin = Mth.floor(entityShip.x - r);
        final int xMax = Mth.floor(entityShip.x + r);
        final int yMax = Mth.floor(entityShip.y);
        final int zMin = Mth.floor(entityShip.z - r);
        final int zMax = Mth.floor(entityShip.z + r);
        final int extraDownLevels = Mth.ceil(h);

        final VertexConsumer vc = buffer.getBuffer(VS$SHADOW_RENDER_TYPE);
        final BlockPos.MutableBlockPos cellPos = new BlockPos.MutableBlockPos();
        final DimensionType dim = level.dimensionType();
        final Quaternionf rotation = VS$ROTATION.set(transform.getShipToWorldRotation());
        final Vector3dc scale = transform.getShipToWorldScaling();

        for (int z = zMin; z <= zMax; z++) {
            for (int x = xMin; x <= xMax; x++) {
                shipToWorld.transformPosition(x + 0.5, yMax, z + 0.5, VS$FIRST_WORLD);
                final double firstWorldX = VS$FIRST_WORLD.x;
                final double firstWorldY = VS$FIRST_WORLD.y;
                final double firstWorldZ = VS$FIRST_WORLD.z;

                for (int step = 0; step <= extraDownLevels; step++) {
                    final double targetY = firstWorldY - step;
                    final int sx, sy, sz;
                    if (step == 0) {
                        sx = x; sy = yMax; sz = z;
                    } else {
                        worldToShip.transformPosition(firstWorldX, targetY, firstWorldZ, VS$SHIP_LOOKUP);
                        sx = Mth.floor(VS$SHIP_LOOKUP.x);
                        sy = Mth.floor(VS$SHIP_LOOKUP.y);
                        sz = Mth.floor(VS$SHIP_LOOKUP.z);
                    }
                    // Per-step blockWeight is uniform across columns — vanilla parity (alpha depends on Y-level offset from entity, not on per-cell world Y which would vary across a tilted deck).
                    final float blockWeight = weight - step * 0.5f;
                    vs$drawShadow(sx, sy, sz, firstWorldX, targetY, firstWorldZ,
                        wx, wy, wz, blockWeight, cellPos, level, dim, entityShip, r,
                        poseStack, rotation, scale, vc);
                }
            }
        }

        ci.cancel();
    }

    @Unique
    private static void vs$drawShadow(
        final int sx, final int sy, final int sz,
        final double targetX, final double targetY, final double targetZ,
        final double wx, final double wy, final double wz,
        final float blockWeight,
        final BlockPos.MutableBlockPos cellPos, final LevelReader level,
        final DimensionType dim, final Vector3d entityShip,
        final float r, final PoseStack poseStack, final Quaternionf rotation,
        final Vector3dc scale, final VertexConsumer vc
    ) {
        cellPos.set(sx, sy, sz);
        final BlockPos below = cellPos.below();
        final BlockState state = level.getChunk(cellPos).getBlockState(below);
        if (state.getRenderShape() == RenderShape.INVISIBLE) return;
        final int rawLight = level.getMaxLocalRawBrightness(cellPos);
        if (rawLight <= 3) return;
        if (!state.isCollisionShapeFullBlock(level, below)) return;
        final VoxelShape shape = state.getShape(level, below);
        if (shape.isEmpty()) return;

        final float brightness = LightTexture.getBrightness(dim, rawLight);
        float alpha = blockWeight * 0.5f * brightness;
        if (alpha < 0.0f) return;
        if (alpha > 1.0f) alpha = 1.0f;

        final AABB aabb = shape.bounds();
        // Corner offsets from cell-shadow-center in ship-local frame; ship rotation tilts the quad with the deck.
        final float ex1 = (float) (aabb.minX - 0.5);
        final float ex2 = (float) (aabb.maxX - 0.5);
        final float ez1 = (float) (aabb.minZ - 0.5);
        final float ez2 = (float) (aabb.maxZ - 0.5);

        // UV from cell's shipyard delta from entity (vanilla parity for non-rotated ships).
        final float uvEx1 = (float) ((sx + aabb.minX) - entityShip.x);
        final float uvEx2 = (float) ((sx + aabb.maxX) - entityShip.x);
        final float uvEz1 = (float) ((sz + aabb.minZ) - entityShip.z);
        final float uvEz2 = (float) ((sz + aabb.maxZ) - entityShip.z);

        final float u1 = -uvEx1 / 2.0f / r + 0.5f;
        final float u2 = -uvEx2 / 2.0f / r + 0.5f;
        final float v1 = -uvEz1 / 2.0f / r + 0.5f;
        final float v2 = -uvEz2 / 2.0f / r + 0.5f;

        poseStack.pushPose();
        // World-axis shift to target position (relative to the entity), then ship rotation/scale.
        poseStack.translate(targetX - wx, targetY - wy, targetZ - wz);
        poseStack.mulPose(rotation);
        poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

        final PoseStack.Pose pose = poseStack.last();
        vs$shadowVertex(pose, vc, alpha, ex1, 0.0f, ez1, u1, v1);
        vs$shadowVertex(pose, vc, alpha, ex1, 0.0f, ez2, u1, v2);
        vs$shadowVertex(pose, vc, alpha, ex2, 0.0f, ez2, u2, v2);
        vs$shadowVertex(pose, vc, alpha, ex2, 0.0f, ez1, u2, v1);

        poseStack.popPose();
    }

    @Unique
    private static void vs$shadowVertex(
        final PoseStack.Pose pose, final VertexConsumer vc, final float alpha,
        final float x, final float y, final float z, final float u, final float v
    ) {
        final Vector3f vec = pose.pose().transformPosition(x, y, z, VS$VERTEX);
        vc.vertex(vec.x(), vec.y(), vec.z(), 1.0f, 1.0f, 1.0f, alpha,
            u, v, OverlayTexture.NO_OVERLAY, 15728880, 0.0f, 1.0f, 0.0f);
    }

}
