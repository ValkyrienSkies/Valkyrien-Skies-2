package org.valkyrienskies.mod.mixin.mod_compat.create.cannons;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

@Mixin(CannonMountBlockEntity.class)
public abstract class MixinCannonMount extends KineticBlockEntity implements ControlPitchContraption.Block {

    private Integer cannonID = null;
    private boolean alreadyAdded = false;

    Vector3d recoilVec = null;
    double recoilForce = 0;

    public MixinCannonMount(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }
/* TODO
    @Override
    public void cacheRecoilVector(Vec3 vector, AbstractContraptionEntity cannon) {
        recoilVec = VectorConversionsMCKt.toJOML(vector);
    }

 */

    @Unique
    private void handleAssembly() {
        LoadedServerShip ship = null;
        if (!level.isClientSide) {
            if (VSGameUtilsKt.getShipObjectManagingPos(level, getBlockPos()) != null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) level, getBlockPos());
            }
        }

        if (ship != null) {
            if (!alreadyAdded && cannonID == null) {
                Vector3dc pos = VectorConversionsMCKt.toJOMLD(worldPosition);
                //final CannonCreateData data = new CannonCreateData(pos);
                //cannonID = CannonController.getOrCreate(ship).addCannon(data);
                alreadyAdded = true;
            }
        }
    }

    @Unique
    private void handleDisassembly() {
        LoadedServerShip ship = null;
        if (!level.isClientSide) {
            if (VSGameUtilsKt.getShipObjectManagingPos(level, getBlockPos()) != null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) level, getBlockPos());
            }
        }
        if (cannonID != null) {
            //CannonController.getOrCreate(ship).removeCannon(cannonID);
            cannonID = null;
            alreadyAdded = false;
            recoilVec = null;
        }

    }

    @Unique
    private void handleController() {
        //do stuff here (I like to have the inject call to a separate function so the breakpoint will work properly for this function)

        LoadedServerShip ship = null;
        if (!level.isClientSide) {
            if (VSGameUtilsKt.getShipObjectManagingPos(level, getBlockPos()) != null) {
                ship = VSGameUtilsKt.getShipObjectManagingPos((ServerLevel) level, getBlockPos());
            }
        }
        if (ship != null) {
            if (alreadyAdded && cannonID != null) {
                if (recoilVec != null) {
                    //final CannonUpdateData data = new CannonUpdateData(recoilVec);
                    //CannonController.getOrCreate(ship).updateCannon(cannonID, data);
                }
            }
            if (this.isRemoved()) {
                if (cannonID != null) {
                    //CannonController.getOrCreate(ship).removeCannon(cannonID);
                    cannonID = null;
                    alreadyAdded = false;
                }
            }
        }
    }
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void injectTick(CallbackInfo ci) {
        handleController();
    }

    @Inject(method = "assemble", at = @At("RETURN"), remap = false)
    private void injectAssemble(CallbackInfo ci) {
        handleAssembly();
    }

    @Inject(method = "disassemble", at = @At("HEAD"), remap = false)
    private void injectDisassemble(CallbackInfo ci) {
        handleDisassembly();
    }

    @Unique
    private CompoundTag writeToCompound(CompoundTag compound, boolean clientPacket){
        //write here
        compound.putBoolean("alreadyAdded", alreadyAdded);
        if (cannonID != null) {
            compound.putInt("cannonID", cannonID);
        }
        return compound;
    }
    @Inject(method = "write", at = @At("HEAD"), cancellable = true, remap = false)
    private void injectWrite(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        compound = writeToCompound(compound,clientPacket);
        super.write(compound, clientPacket);
        ci.cancel();
    }

    @Unique
    private CompoundTag readFromCompound(CompoundTag compound, boolean clientPacket){
        //read (and remove before it passes up?) here
        alreadyAdded = compound.getBoolean("alreadyAdded");
        if (compound.contains("cannonID")) {
            cannonID = compound.getInt("cannonID");
        }
        return compound;
    }
    @Inject(method = "read", at = @At("HEAD"), cancellable = true, remap = false)
    private void injectRead(CompoundTag compound, boolean clientPacket, CallbackInfo ci) {
        compound = readFromCompound(compound,clientPacket);
        super.read(compound, clientPacket);
        ci.cancel();
    }

}
