package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectDraw;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.valkyrienskies.mod.compat.flywheel.FlywheelShipPieces;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.ShipVisualEmbedding;

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class MixinIndirectCullingGroup {
    @Shadow @Final private List<IndirectDraw> indirectDraws;

    @Unique
    private ShipVisualEmbedding vs$splitAt(int index) {
        return FlywheelShipPieces.split(((IndirectDrawAccessor) indirectDraws.get(index)).getInstancer().environment);
    }

    @WrapOperation(method = {"submitSolid", "submitTransparent"}, at = @At(value = "INVOKE", target =
        "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectCullingGroup$MultiDraw;submit(Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;)V"))
    private void vs$submitPieces(@Coerce Object draw, GlProgram program, Operation<Void> original) {
        var batch = (MultiDrawAccessor) draw;
        if (!batch.isEmbedded() || !FlywheelShipPieces.hasPieces()) {
            original.call(draw, program);
            return;
        }
        int start = batch.getStart();
        while (start < batch.getEnd()) {
            var split = vs$splitAt(start);
            int end = start + 1;
            while (end < batch.getEnd() && vs$splitAt(end) == split) end++;
            final int first = start, limit = end;
            if (split == null && first == batch.getStart() && limit == batch.getEnd()) {
                original.call(draw, program);
            } else if (split == null) {
                vs$submitRange(program, first, limit);
            } else {
                FlywheelShipPieces.draw(split, program.handle(), () -> vs$submitRange(program, first, limit));
            }
            start = end;
        }
    }

    @Unique
    private static void vs$submitRange(GlProgram program, int start, int end) {
        GlCompat.safeMultiDrawElementsIndirect(program, GL11.GL_TRIANGLES, GL11.GL_UNSIGNED_INT, start, end, 36L);
    }
}
