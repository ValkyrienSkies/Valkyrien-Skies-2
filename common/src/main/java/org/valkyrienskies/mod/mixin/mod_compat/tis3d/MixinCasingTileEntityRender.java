package org.valkyrienskies.mod.mixin.mod_compat.tis3d;

import li.cil.tis3d.client.renderer.block.entity.CasingBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(CasingBlockEntityRenderer.class)
public abstract class MixinCasingTileEntityRender {
    @ModifyVariable(remap = false, method = "isBackFace",
        at = @At("STORE"), ordinal = 0)
    private Vec3 vs$isBackFace(final Vec3 original, final BlockPos position) {
        final Ship ship = ValkyrienSkies.getShipManagingBlock(Minecraft.getInstance().level, position);
        if (ship != null) {
            return ValkyrienSkies.toMinecraft(ValkyrienSkies.toJOML(ValkyrienSkies.positionToShip(ship, original)));
        }
        return original;
    }
}

