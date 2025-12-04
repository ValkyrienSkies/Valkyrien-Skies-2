package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.BezierConnection.SegmentAngles;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(targets = "com.simibubi.create.content.trains.track.TrackVisual$BezierTrackVisual")
public class MixinBezierTrackVisual {
    @WrapOperation(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;setChanged()V")
    )
    private static void stackToShip(TransformedInstance instance, Operation<Void> original,
        @Local(argsOnly = true) BezierConnection bc, @Local SegmentAngles sa, @Local(name = "i") int i) {
        ClientShip ship = VSClientGameUtils.getClientShip(bc.bePositions.getFirst().getX(), bc.bePositions.getFirst().getY(), bc.bePositions.getFirst().getZ());
        if(ship != null) {
            instance = (TransformedInstance) instance.light(LevelRenderer.getLightColor(Minecraft.getInstance().level, sa.lightPosition[i].offset(bc.bePositions.getFirst())));
        }
        original.call(instance);
    }
}
