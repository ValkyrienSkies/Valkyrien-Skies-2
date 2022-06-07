package org.valkyrienskies.mod.mixinducks.client;

import net.minecraft.world.phys.HitResult;

public interface MinecraftDuck {

    void vs$setOriginalCrosshairTarget(HitResult h);

    HitResult vs$getOriginalCrosshairTarget();

}
