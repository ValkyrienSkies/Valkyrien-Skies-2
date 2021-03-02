package org.valkyrienskies.mod.mixin.accessors.client.world;

import net.minecraft.client.world.ClientChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientChunkManager.ClientChunkMap.class)
public interface ClientChunkManagerClientChunkMapAccessor {
	@Invoker(value = "isInRadius")
	boolean callIsInRadius(int chunkX, int chunkZ);
}
