package org.valkyrienskies.mod.mixinducks.client;

import net.minecraft.util.hit.HitResult;

public interface MinecraftClientDuck {

    void vs$setOriginalCrosshairTarget(HitResult h);

    HitResult vs$getOriginalCrosshairTarget();

}
