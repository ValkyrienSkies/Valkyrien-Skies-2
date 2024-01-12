package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import com.simibubi.create.foundation.render.SuperRenderTypeBuffer;
import com.simibubi.create.foundation.utility.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value={DeployTool.class})
public class MixinDeployTool extends SchematicToolBase {
    @Redirect(
        method = "Lcom/simibubi/create/content/schematics/client/tools/DeployTool;renderTool(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/simibubi/create/foundation/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    public void redirectTranslate(PoseStack ms, double _x, double _y, double _z) {
    }

    @Inject(
        method = "Lcom/simibubi/create/content/schematics/client/tools/DeployTool;renderTool(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/simibubi/create/foundation/render/SuperRenderTypeBuffer;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    public void mixinRenderTool(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, CallbackInfo ci) {
        float pt = AnimationTickHolder.getPartialTicks();
        double x = Mth.lerp(pt, lastChasingSelectedPos.x, chasingSelectedPos.x);
        double y = Mth.lerp(pt, lastChasingSelectedPos.y, chasingSelectedPos.y);
        double z = Mth.lerp(pt, lastChasingSelectedPos.z, chasingSelectedPos.z);
        Ship ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, x, y, z);

        AABB bounds = schematicHandler.getBounds();
        Vec3 center = bounds.getCenter();
        int centerX = (int) center.x;
        int centerZ = (int) center.z;

        if (ship != null) {
            VSClientGameUtils.transformRenderWithShip(ship.getTransform(), ms, x - centerX, y, z - centerZ, camera.x, camera.y, camera.z);
        } else {
            ms.translate(x - centerX - camera.x, y - camera.y, z - centerZ - camera.z);
        }
    }

    @Unique
    @Override
    public boolean handleRightClick() {
        return false;
    }

    @Unique
    @Override
    public boolean handleMouseWheel(double delta) {
        return false;
    }
}
