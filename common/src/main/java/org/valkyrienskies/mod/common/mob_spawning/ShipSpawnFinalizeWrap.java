package org.valkyrienskies.mod.common.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Lives in common (not the mixin package) because mixin disallows references to mixin-package classes from outside the mixin system.
public final class ShipSpawnFinalizeWrap {

    private ShipSpawnFinalizeWrap() {
    }

    public static SpawnGroupData wrap(
        final Mob mob, final ServerLevelAccessor accessor, final DifficultyInstance difficulty,
        final MobSpawnType spawnType, final SpawnGroupData groupData, final CompoundTag tag,
        final Operation<SpawnGroupData> original
    ) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos((Level) accessor.getLevel(), mob.blockPosition());
        if (ship == null) return original.call(mob, accessor, difficulty, spawnType, groupData, tag);
        ShipSpawnFinalizeContext.push(ship);
        try {
            return original.call(mob, accessor, difficulty, spawnType, groupData, tag);
        } finally {
            ShipSpawnFinalizeContext.pop();
        }
    }
}
