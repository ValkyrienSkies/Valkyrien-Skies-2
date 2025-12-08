package org.valkyrienskies.mod.common.entity

import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.mod.api.SeatedControllingPlayer
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.isBlockInShipyard
import org.valkyrienskies.mod.common.networking.PacketPlayerDriving
import org.valkyrienskies.mod.common.vsCore

open class ShipMountingEntity(type: EntityType<ShipMountingEntity>, level: Level) : Entity(type, level) {
    // Decides if this entity controls the ship it is in.
    // Only needs to be set serverside
    var isController = false

    init {
        // Don't prevent blocks colliding with this entity from being placed
        blocksBuilding = false
        // Don't collide with terrain
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide && passengers.isEmpty()) {
            // Kill this entity if nothing is riding it
            kill()
            return
        }

        if (level().getLoadedShipManagingPos(blockPosition()) != null)
            sendDrivingPacket()
    }

    // This is a partial fix for mounting ships that have been deleted
    // TODO: Make a full fix eventually
    /*
    override fun positionRider(entity: Entity) {
        if (level().isBlockInShipyard(position()) && level().getShipManagingPos(position()) == null) {
            // Stop rider positioning if we can't find the ship
            entity.removeVehicle()
            return
        }
        super.positionRider(entity)
    }

     */

    // This is a partial fix for mounting ships that have been deleted
    // TODO: Make a full fix eventually
    override fun getDismountLocationForPassenger(livingEntity: LivingEntity): Vec3 {
        if (level().isBlockInShipyard(position()) && level().getShipManagingPos(position()) == null) {
            // Don't teleport to the ship if we can't find the ship
            return livingEntity.position()
        }
        return super.getDismountLocationForPassenger(livingEntity)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {}

    override fun addAdditionalSaveData(compound: CompoundTag) {}

    override fun defineSynchedData() {}

    override fun remove(removalReason: RemovalReason) {
        if (this.isController && !level().isClientSide)
            (level().getLoadedShipManagingPos(blockPosition()) as LoadedServerShip?)
                ?.setAttachment<SeatedControllingPlayer>(null)
        super.remove(removalReason)
    }

    // basic controller compat, mostly from Tweaked Controllers and Controllable, from GetItemFromBlock and Mrcrawfish, respectively
    private var selectedGamepad = -1
    private val state = GLFWGamepadState.create()

    // copied pretty much verbatim from tweaked controllers
    private fun checkGamepad() {
        if (selectedGamepad < 0) {
            var uniqueGamepadID = -1
            for (i in 0 until 16) {
                if (!GLFW.glfwJoystickIsGamepad(i)) continue
                if (uniqueGamepadID == -1) {
                    uniqueGamepadID = i
                } else if (uniqueGamepadID >= 0) {
                    uniqueGamepadID = -2
                }
                GLFW.glfwGetGamepadState(i, state)
                for (b in 0..14) {
                    if (state.buttons(b).toInt() != GLFW.GLFW_PRESS) continue
                    selectedGamepad = i
                    break
                }
            }
            // ig we need to do this
            if (selectedGamepad < 0 && uniqueGamepadID >= 0) {
                selectedGamepad = uniqueGamepadID
            }
        }
        if (selectedGamepad < 0 || !GLFW.glfwJoystickIsGamepad(selectedGamepad)) {
            selectedGamepad = -1
            state.buttons().put(0)
        } else {
            GLFW.glfwGetGamepadState(selectedGamepad, state)
        }
    }
    // very basic controller compat, if it works then it works
    private fun sendDrivingPacket() {
        if (!level().isClientSide) return

        val mc = Minecraft.getInstance()
        val opts = mc.options

        checkGamepad()

        // praise be getitemfromblock
        fun isPressed(binding: KeyMapping?, buttonIndex: Int?): Boolean {
            val kb = binding?.isDown ?: false
            val gp = buttonIndex?.let {
                selectedGamepad >= 0 && state.buttons(it).toInt() == GLFW.GLFW_PRESS
            } ?: false
            return kb || gp
        }

        // control deez nuts
        val forward = isPressed(opts.keyUp, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)
        val backward = isPressed(opts.keyDown, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)
        val left = isPressed(opts.keyLeft, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)
        val right = isPressed(opts.keyRight, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)
        val up = isPressed(opts.keyJump, GLFW.GLFW_GAMEPAD_BUTTON_A)
        val down = isPressed(VSKeyBindings.shipDown.get(), GLFW.GLFW_GAMEPAD_BUTTON_B)
        val cruise = isPressed(VSKeyBindings.shipCruise.get(), GLFW.GLFW_GAMEPAD_BUTTON_Y)
        val sprint = this.controllingPassenger?.isSprinting == true

        val leftStickX = if (selectedGamepad >= 0) state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X) else 0f
        val leftStickY = if (selectedGamepad >= 0) state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y) else 0f
        val rightStickY = if (selectedGamepad >= 0) state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y) else 0f

        // stops ship helms from constantly turning, can probably be axed if i make full analog support later
        fun applyDeadzone(value: Float, deadzone: Float = 0.1f) =
            if (kotlin.math.abs(value) < deadzone) 0f else value

        val impulse = Vector3f()
        impulse.x = applyDeadzone(-leftStickX) + if (left == right) 0f else if (left) 1f else -1f
        impulse.z = applyDeadzone(-leftStickY) + if (forward == backward) 0f else if (forward) 1f else -1f
        impulse.y = applyDeadzone(-rightStickY) + if (up == down) 0f else if (up) 1f else -1f

        val magnitude = impulse.length()
        if (magnitude > 1f) impulse.mul(1f / magnitude)
        
        vsCore.simplePacketNetworking.run {
            PacketPlayerDriving(impulse, sprint, cruise).sendToServer()
        }
    }

    override fun getControllingPassenger(): LivingEntity? {
        return if (isController) {
            this.passengers.getOrNull(0) as? LivingEntity
        } else {
            null
        }
    }

    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> {
        return ClientboundAddEntityPacket(this)
    }
}
