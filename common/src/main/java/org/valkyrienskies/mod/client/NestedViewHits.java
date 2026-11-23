package org.valkyrienskies.mod.client;

import java.util.function.Consumer;
import net.minecraft.world.phys.HitResult;

/** A portal owner can replace (or hide) the hit without updating vanilla's separate name-tag target. */
public final class NestedViewHits {
    private NestedViewHits() {}

    public static <E> Runnable enter(HitResult hit, E entity, Consumer<HitResult> setHit, Consumer<E> setEntity) {
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) setEntity.accept(null);
        return () -> {
            setHit.accept(hit);
            setEntity.accept(entity);
        };
    }
}
