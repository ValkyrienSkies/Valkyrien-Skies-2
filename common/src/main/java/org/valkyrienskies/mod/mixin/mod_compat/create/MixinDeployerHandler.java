package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixin.accessors.entity.EntityAccessor;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IDeployerBehavior;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IDeployerBehavior.WorkingMode;

@Pseudo
@Mixin(DeployerHandler.class)
public abstract class MixinDeployerHandler {
    @ModifyExpressionValue(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            )
    )
    private static Vec3 setRayOrigin(Vec3 original, @Local(argsOnly = true, ordinal = 0) DeployerFakePlayer player, @Local(argsOnly = true, ordinal = 0) Vec3 vec3, @Share("mode") LocalBooleanRef place_in_world) {
        BlockEntity blockEntity = player.level().getBlockEntity(BlockPos.containing(vec3));
        if (blockEntity instanceof IDeployerBehavior behavior) {
            place_in_world.set(behavior.valkyrienskies$get_working_mode().get() == WorkingMode.IN_WORLD);
        }
        if(place_in_world.get())
            return VSGameUtilsKt.toWorldCoordinates(player.level(), original);
        return original;
    }

    @ModifyExpressionValue(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 1
            )
    )
    private static Vec3 setRayTarget(Vec3 original, @Local(argsOnly = true, ordinal = 0) DeployerFakePlayer player, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return VSGameUtilsKt.toWorldCoordinates(player.level(), original);
        return original;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            ),
            index = 1
    )
    private static AABB aabbToWorld(AABB par2, @Local(ordinal = 0) ServerLevel world, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return VSGameUtilsKt.transformAabbToWorld(world, par2);
        return par2;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 1
            )
    )
    private static double scale(double factor, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return factor+2/64f;
        return factor;
    }

    @WrapOperation(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerFakePlayer;setPos(DDD)V"
            )
    )
    private static void setPos(DeployerFakePlayer player, double x, double y, double z, Operation<Void> original,
                               @Share("mode") LocalBooleanRef original_behaviour) {
        if (!original_behaviour.get() && VSGameUtilsKt.isBlockInShipyard(player.level(), x, y, z)) {
            final EntityAccessor accessor = (EntityAccessor) (Object) player;
            accessor.setPosNoUpdates(new Vec3(x, y, z));
            accessor.setBlockPosition(BlockPos.containing(x, y, z));
            return;
        }
        original.call(player, x, y, z);
    }

    @WrapOperation(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            )
    )
    private static BlockHitResult clip(ServerLevel instance, ClipContext clipContext, Operation<BlockHitResult> original,
                                       @Local(argsOnly = true, ordinal = 0) Vec3 vec,
                                       @Local(argsOnly = true, ordinal = 0) BlockPos clickedPos,
                                       @Local(argsOnly = true, ordinal = 1) Vec3 extensionVector,
                                       @Share("mode") LocalBooleanRef original_behaviour) {
        if (original_behaviour.get()) {
            final BlockHitResult result = RaycastUtilsKt.clipIncludeShips(instance, clipContext, false);
            if (result.getType() == HitResult.Type.MISS) {
                return RaycastUtilsKt.clipIncludeShips(instance,
                        new ClipContext(
                                clipContext.getFrom(),
                                VSGameUtilsKt.toWorldCoordinates(instance,
                                        vec.add(extensionVector.scale(5 / 2f - 1 / 64f))),
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                null
                        ),
                        false
                );
            }
            return result;
        }

        if (VSGameUtilsKt.isBlockInShipyard(instance, clickedPos)) {
            return RaycastUtilsKt.vanillaClip(instance, clipContext);
        }

        return original.call(instance, clipContext);
    }

    @ModifyExpressionValue(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/BlockHitResult;getBlockPos()Lnet/minecraft/core/BlockPos;"
            )
    )
    private static BlockPos redirectToTrue(BlockPos original, @Local(argsOnly = true, ordinal = 0) BlockPos clickedPos, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return clickedPos;
        return original;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    ordinal = 0
            ),
            index = 0
    )
    private static BlockPos replace1(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z",
                    ordinal = 0
            ),
            index = 1
    )
    private static BlockPos replace2(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
                    ordinal = 0
            ),
            index = 1
    )
    private static BlockPos replace3(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/BlockHelper;extinguishFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
                    ordinal = 0
            ),
            index = 2
    )
    private static BlockPos replace4(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V",
                    ordinal = 0
            ),
            index = 1
    )
    private static BlockPos replace5(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
                    ordinal = 0
            ),
            index = 2
    )
    private static BlockPos replace6(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
                    ordinal = 0
            ),
            index = 1
    )
    private static BlockPos replace7(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerHandler;tryHarvestBlock(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayerGameMode;Lnet/minecraft/core/BlockPos;)Z",
                    ordinal = 0
            ),
            index = 2
    )
    private static BlockPos replace8(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V",
                    ordinal = 0
            ),
            index = 1
    )
    private static BlockPos replace9(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V",
                    ordinal = 1
            ),
            index = 1
    )
    private static BlockPos replace10(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/apache/commons/lang3/tuple/Pair;of(Ljava/lang/Object;Ljava/lang/Object;)Lorg/apache/commons/lang3/tuple/Pair;"
            ),
            index = 0,
            remap = false
    )
    private static Object replace11(Object par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
                    ordinal = 1
            ),
            index = 1
    )
    private static BlockPos replace12(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/deployer/DeployerHandler;safeOnUse(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"
            ),
            index = 2
    )
    private static BlockPos replace13(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }

    @ModifyArg(
            method = "activateInner",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BaseFireBlock;canBePlacedAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z"
            ),
            index = 1
    )
    private static BlockPos replace14(BlockPos par1, @Local(ordinal = 0) BlockHitResult result, @Share("mode") LocalBooleanRef original_behaviour) {
        if(original_behaviour.get())
            return result.getBlockPos();
        return par1;
    }
}
