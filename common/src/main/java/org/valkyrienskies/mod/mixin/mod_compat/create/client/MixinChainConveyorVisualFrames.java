package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.ChainConveyorCrossFrameDuck;

// Create aims the guards at each chain only when the visual is set up; a chain to another frame turns as the ship
// moves, so re-aim them every frame (see ChainConveyorFrames).
@Mixin(value = ChainConveyorVisual.class, remap = false)
public abstract class MixinChainConveyorVisualFrames extends SingleAxisRotatingVisual<ChainConveyorBlockEntity> {
    @Shadow
    @Final
    private List<TransformedInstance> guards;

    private MixinChainConveyorVisualFrames(final VisualizationContext context, final ChainConveyorBlockEntity be,
        final float partialTick, final Model model) {
        super(context, be, partialTick, model);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void vs$aimGuards(final DynamicVisual.Context ctx, final CallbackInfo ci) {
        final Level level = blockEntity.getLevel();
        final BlockPos pos = blockEntity.getBlockPos();
        boolean crossFrame = false;
        for (final BlockPos connection : blockEntity.connections) {
            if (ChainConveyorFrames.isCrossFrame(level, pos, connection)) {
                crossFrame = true;
                break;
            }
        }
        if (!crossFrame) return;
        ((ChainConveyorCrossFrameDuck) blockEntity).vs$refreshCrossFrame();
        // Same order as setupGuards: the wheel, then one guard per connection with stats.
        int i = 1;
        for (final BlockPos connection : blockEntity.connections) {
            final ConnectionStats stats = blockEntity.connectionStats.get(connection);
            if (stats == null) continue;
            if (i >= guards.size()) return;
            final Vec3 diff = stats.end().subtract(stats.start());
            final float yaw = (float) (Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z));
            guards.get(i++).setIdentityTransform().translate(getVisualPosition()).center().rotateYDegrees(yaw).uncenter()
                .setChanged();
        }
    }
}
