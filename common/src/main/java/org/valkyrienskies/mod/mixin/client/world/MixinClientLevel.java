package org.valkyrienskies.mod.mixin.client.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Random;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.core.hooks.VSCoreHooksKt;
import org.valkyrienskies.core.util.AABBdUtilKt;
import org.valkyrienskies.mod.client.audio.SimpleSoundInstanceOnShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {
    @Shadow
    @Final
    private LevelRenderer levelRenderer;
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract void doAnimateTick(int i, int j, int k, int l, Random random, boolean bl,
        BlockPos.MutableBlockPos mutableBlockPos);

    @Shadow
    @Final
    private Int2ObjectMap<Entity> entitiesById;

    @Inject(method = "disconnect", at = @At("TAIL"))
    private void afterDisconnect(final CallbackInfo ci) {
        VSCoreHooksKt.getCoreHooks().afterDisconnect();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(final BooleanSupplier shouldKeepTicking, final CallbackInfo ci) {
        // Drag entities
        EntityDragger.INSTANCE.dragEntitiesWithShips(entitiesById.values());
        VSGameUtilsKt.getShipObjectWorld(minecraft).getNetworkManager()
            .tick(minecraft.getConnection().getConnection().getRemoteAddress());
    }

    // do particle ticks for ships near the player
    @Inject(
        at = @At("TAIL"),
        method = "animateTick"
    )
    private void afterAnimatedTick(final int posX, final int posY, final int posZ, final CallbackInfo ci) {
        final AABBd aabb = AABBdUtilKt.expand(new AABBd(posX, posY, posZ, posX, posY, posZ), 32.0);
        final Vector3d temp = new Vector3d();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(ClientLevel.class.cast(this), aabb)) {
            final Vector3d inShip = ship.getWorldToShip().transformPosition(temp.set(posX, posY, posZ));
            originalAnimateTick((int) inShip.x, (int) inShip.y, (int) inShip.z);
        }
    }

    private void originalAnimateTick(final int posX, final int posY, final int posZ) {
        final Random random = new Random();
        boolean bl = false;
        if (this.minecraft.gameMode.getPlayerMode() == GameType.CREATIVE) {
            for (final ItemStack itemStack : this.minecraft.player.getHandSlots()) {
                if (itemStack.getItem() == Blocks.BARRIER.asItem()) {
                    bl = true;
                    break;
                }
            }
        }

        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = 0; j < 667; ++j) {
            doAnimateTick(posX, posY, posZ, 16, random, bl, mutableBlockPos);
            doAnimateTick(posX, posY, posZ, 32, random, bl, mutableBlockPos);
        }
    }

    @Redirect(
        at = @At(
            value = "NEW",
            target = "net/minecraft/client/resources/sounds/SimpleSoundInstance"
        ),
        method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
    )
    private SimpleSoundInstance redirectNewSoundInstance(final SoundEvent soundEvent, final SoundSource soundSource,
        final float volume, final float pitch, final double x, final double y, final double z) {

        final Ship ship = VSGameUtilsKt.getShipManagingPos(ClientLevel.class.cast(this), x, y, z);
        if (ship != null) {
            return new SimpleSoundInstanceOnShip(soundEvent, soundSource, pitch, volume, x, y, z,
                ship);
        }

        return new SimpleSoundInstance(soundEvent, soundSource, volume, pitch, x, y, z);
    }
}
