package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import com.simibubi.create.foundation.utility.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * SchematicTransformation is responsible for the render transform of the schematic preview
 * <p>
 * Create applies both the camera and schematic positions in the same operation, the latter of which does not respect ship-space.
 * This mixin redirects the operation and fixes it by extracting the position components from the argument.
 * I can't think of a better way to get around it.
 */
@Mixin(value = {SchematicTransformation.class}, remap = false)
public abstract class MixinSchematicTransformation {
    @Shadow
    private BlockPos target;
    @Shadow
    private Vec3 chasingPos;
    @Shadow
    private Vec3 prevChasingPos;

    @Redirect(
            method = {"applyTransformations(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/jozufozu/flywheel/util/transform/TransformStack;translate(Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object redirectTranslate(TransformStack instance, Vec3 orig) {
        PoseStack ms = (PoseStack)instance;
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, target.getX(), target.getY(), target.getZ());

        if (ship != null) {
            float pt = AnimationTickHolder.getPartialTicks();
            Vec3 pos = VecHelper.lerp(pt, prevChasingPos, chasingPos);
            Vec3 camera = pos.subtract(orig);
            VSClientGameUtils.transformRenderWithShip(ship.getTransform(), ms, pos.x, pos.y, pos.z, camera.x, camera.y, camera.z);
            return instance;
        } else {
            return instance.translate(orig);
        }
    }
}
