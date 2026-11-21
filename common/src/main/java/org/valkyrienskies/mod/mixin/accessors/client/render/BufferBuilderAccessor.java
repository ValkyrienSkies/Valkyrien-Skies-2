package org.valkyrienskies.mod.mixin.accessors.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Invoker("ensureCapacity")
    void vs$ensureCapacity(int bytes);

    @Accessor("buffer")
    ByteBuffer vs$getBuffer();

    @Accessor("nextElementByte")
    int vs$getNextElementByte();

    @Accessor("nextElementByte")
    void vs$setNextElementByte(int value);

    @Accessor("vertices")
    int vs$getVertices();

    @Accessor("vertices")
    void vs$setVertices(int value);
}
