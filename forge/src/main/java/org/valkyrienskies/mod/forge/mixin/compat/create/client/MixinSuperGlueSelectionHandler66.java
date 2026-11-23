package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import java.util.Optional;

/**
 * This mixin is for Create 6.0.6
 * <br>
 * For the 6.0.7+ equivalent, see {@link MixinSuperGlueSelectionHandler67}
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler")
public abstract class MixinSuperGlueSelectionHandler66 {
    @WrapOperation(method = "tick", require = 1, at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/multiplayer/ClientLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <E extends net.minecraft.world.entity.Entity> java.util.List<E> vs$glueAtSplitImages(
        net.minecraft.client.multiplayer.ClientLevel level, Class<E> type, AABB box, Operation<java.util.List<E>> query) {
        return VSClientGameUtils.glueCandidates(level, type, box, query.call(level, type, box));
    }

    @WrapOperation(method = "tick", require = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private Optional<Vec3> vs$clipCandidate(AABB box, Vec3 from, Vec3 to, Operation<Optional<Vec3>> original) {
        final var level = Minecraft.getInstance().level;
        final var ship = VSGameUtilsKt.getLoadedShipManagingPos(level, box.getCenter());
        return ship == null ? original.call(box, from, to) : VSClientGameUtils.clipGlueCandidate(level, ship, box, from, to);
    }
}
