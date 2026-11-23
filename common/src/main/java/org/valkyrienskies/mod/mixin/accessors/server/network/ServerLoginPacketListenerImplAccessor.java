package org.valkyrienskies.mod.mixin.accessors.server.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerImplAccessor {
    @Accessor("gameProfile")
    GameProfile valkyrienskies$getGameProfile();

    @Accessor("connection")
    Connection valkyrienskies$getConnection();
}
