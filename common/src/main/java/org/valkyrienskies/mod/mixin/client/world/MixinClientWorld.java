package org.valkyrienskies.mod.mixin.client.world;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.QueryableShipData;
import org.valkyrienskies.core.game.ShipObject;
import org.valkyrienskies.core.game.ShipObjectWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtils;

import java.util.function.BooleanSupplier;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld implements IShipObjectWorldProvider {


    private final ShipObjectWorld shipObjectWorld = new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Tick the ship world
        shipObjectWorld.tickShips();
    }

    @Inject(
        method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlaySound(double x, double y, double z, SoundEvent sound, SoundCategory category,
                             float volume, float pitch, boolean bl, CallbackInfo ci) {
        final ClientWorld self = ClientWorld.class.cast(this);
        final ShipObject shipObject = VSGameUtils.getShipObjectManagingPos(self, (int) x / 16, (int) z / 16);
        if (shipObject != null) {
            Vector3d newPosition = shipObject.getRenderTransform().getShipToWorldMatrix()
                .transformPosition(new Vector3d(x, y, z));

            playSound(newPosition.x, newPosition.y, newPosition.z, sound, category, volume, pitch, bl);
            ci.cancel();
        }
    }

    @Shadow
    public abstract void playSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch, boolean bl);

}
