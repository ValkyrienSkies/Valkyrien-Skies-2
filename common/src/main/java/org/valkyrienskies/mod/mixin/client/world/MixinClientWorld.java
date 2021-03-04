package org.valkyrienskies.mod.mixin.client.world;

import java.util.function.BooleanSupplier;
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
import org.valkyrienskies.core.game.ships.QueryableShipData;
import org.valkyrienskies.core.game.ships.ShipObject;
import org.valkyrienskies.core.game.ships.ShipObjectWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldProvider;
import org.valkyrienskies.mod.common.VSGameUtils;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld implements IShipObjectWorldProvider {


    private final ShipObjectWorld shipObjectWorld =
        new ShipObjectWorld(new QueryableShipData(), new ChunkAllocator(-7000, 3000));

    @NotNull
    @Override
    public ShipObjectWorld getShipObjectWorld() {
        return shipObjectWorld;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void postTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        // Tick the ship world
        shipObjectWorld.tickShips();
    }

    @Inject(
        method = "playSound(DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlaySound(final double x, final double y, final double z, final SoundEvent sound,
        final SoundCategory category,
        final float volume, final float pitch, final boolean bl, final CallbackInfo ci) {
        final ClientWorld self = ClientWorld.class.cast(this);
        final ShipObject shipObject = VSGameUtils.getShipObjectManagingPos(self, (int) x >> 4, (int) z >> 4);
        if (shipObject != null) {
            // in-world position
            final Vector3d p = shipObject.getRenderTransform()
                .getShipToWorldMatrix().transformPosition(new Vector3d(x, y, z));

            playSound(p.x, p.y, p.z, sound, category, volume, pitch, bl);
            ci.cancel();
        }
    }

    @Shadow
    public abstract void playSound(double x, double y, double z, SoundEvent sound, SoundCategory category, float volume,
        float pitch, boolean bl);

}
