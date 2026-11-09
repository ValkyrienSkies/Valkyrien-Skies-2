package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

@Mixin(EntityType.class)
public abstract class MixinEntityTypeShipSpawnFilter {

    @Inject(
        method = "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;Ljava/util/function/Consumer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;ZZ)Lnet/minecraft/world/entity/Entity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vs$filterShipNaturalSpawn(
        final ServerLevel level, final CompoundTag nbt, final Consumer<? super Entity> consumer,
        final BlockPos pos, final MobSpawnType spawnType,
        final boolean shouldYOffset, final boolean shouldOffsetYMore,
        final CallbackInfoReturnable<Entity> cir
    ) {
        if (pos == null || !vs$isNaturalSpawnType(spawnType)) return;
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) return;
        final EntityType<?> self = (EntityType<?>) (Object) this;
        if (self.is(ValkyrienSkiesMod.NO_NATURAL_SHIP_SPAWN)) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static boolean vs$isNaturalSpawnType(final MobSpawnType type) {
        return type == MobSpawnType.NATURAL
            || type == MobSpawnType.CHUNK_GENERATION
            || type == MobSpawnType.PATROL
            || type == MobSpawnType.MOB_SUMMONED
            || type == MobSpawnType.REINFORCEMENT
            || type == MobSpawnType.JOCKEY
            || type == MobSpawnType.EVENT;
    }
}
