package org.valkyrienskies.mod.forge.gametest

import blusunrize.immersiveengineering.common.gui.BlockEntityInventory
import dev.su5ed.mffs.api.fortron.FortronStorage
import dev.su5ed.mffs.util.FrequencyGrid
import java.util.concurrent.Executor
import me.desht.modularrouters.container.RouterMenu
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.minecraftforge.fml.ModList
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.gametest.*
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile
import rbasamoyai.createbigcannons.network.ServerboundSetFixedCannonMountValuePacket

// Other mods' range and collision checks against ship blocks. Needs the mods at runtime: run with -PvsTestMods.
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
class ModCompatShipGameTests {

    private val stone = Blocks.STONE.defaultBlockState()
    private val shipAt = BlockPos(8, 5, 8)

    private fun GameTestHelper.requireMod(id: String) =
        check(ModList.get().isLoaded(id)) { "mod '$id' isn't loaded; run the game tests with -PvsTestMods" }

    private fun block(id: String): BlockState =
        BuiltInRegistries.BLOCK.get(ResourceLocation(id)).defaultBlockState()

    @GameTest(template = TEST_AREA, batch = "ie_inventory", timeoutTicks = 200)
    fun immersiveEngineeringInventoryOnShipIsUsableNearby(helper: GameTestHelper) {
        helper.requireMod("immersiveengineering")
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.CHEST.defaultBlockState()), helper.absolutePos(shipAt)) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(pos).add(2.0, -0.5, 0.0))
            try {
                helper.check(BlockEntityInventory.isValidForPlayer(helper.level.getBlockEntity(pos)!!, player)) {
                    "IE inventory on a ship 2 blocks away wasn't usable"
                }
            } finally {
                player.removeMock()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "modular_router_menu", timeoutTicks = 200)
    fun modularRouterMenuOnShipStaysValidNearby(helper: GameTestHelper) {
        helper.requireMod("modularrouters")
        helper.withShip(mapOf(BlockPos.ZERO to block("modularrouters:modular_router")), helper.absolutePos(shipAt)) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(pos).add(2.0, -0.5, 0.0))
            try {
                val menu = RouterMenu(0, player.inventory, pos)
                helper.check(menu.stillValid(player)) { "modular router menu for a router on a ship 2 blocks away closed" }
            } finally {
                player.removeMock()
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "mffs_frequency", timeoutTicks = 200)
    fun mffsFortronStorageOnShipIsInRange(helper: GameTestHelper) {
        helper.requireMod("mffs")
        helper.withShip(mapOf(BlockPos.ZERO to Blocks.CHEST.defaultBlockState()), helper.absolutePos(shipAt)) { ship ->
            val owner = helper.level.getBlockEntity(ship.shipPos(0, 0, 0))!!
            val storage = object : FortronStorage {
                override fun getOwner(): BlockEntity = owner
                override fun setStoredFortron(amount: Int) {}
                override fun getStoredFortron() = 0
                override fun getFortronCapacity() = 0
                override fun extractFortron(amount: Int, simulate: Boolean) = 0
                override fun insertFortron(amount: Int, simulate: Boolean) = 0
                override fun getFrequency() = 98765
                override fun setFrequency(frequency: Int) {}
                override fun serializeNBT() = CompoundTag()
                override fun deserializeNBT(tag: CompoundTag) {}
            }
            val grid = FrequencyGrid.instance(false)
            grid.register(storage)
            try {
                val near = BlockPos.containing(ship.worldCenter(ship.shipPos(0, 0, 0))).offset(3, 0, 0)
                helper.check(grid.get(helper.level, near, 5, 98765).contains(storage)) {
                    "fortron storage on a ship 3 blocks away wasn't within a 5 block radius"
                }
            } finally {
                grid.unregister(storage)
            }
        }
    }

    @GameTest(template = TEST_AREA, batch = "cbc_projectile_fall", timeoutTicks = 200)
    fun cannonShotStuckInShipBlockDoesNotFall(helper: GameTestHelper) {
        helper.requireMod("createbigcannons")
        helper.withShip(mapOf(BlockPos.ZERO to stone), helper.absolutePos(shipAt)) { ship ->
            val type = EntityType.byString("createbigcannons:ap_shot").orElseThrow()
            val shot = type.create(helper.level) as AbstractCannonProjectile
            shot.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(BlockPos(2, 2, 2))))
            val shouldFall = method(AbstractCannonProjectile::class.java, "shouldFall")
            shot.setInGround(true)
            shot.setGroundPos(Vec3.atCenterOf(helper.absolutePos(BlockPos(2, 4, 2))))
            helper.check(shouldFall.invoke(shot) as Boolean) { "shot stuck in open air didn't fall; test setup is wrong" }
            shot.setGroundPos(ship.worldCenter(ship.shipPos(0, 0, 0)))
            helper.check(!(shouldFall.invoke(shot) as Boolean)) { "shot stuck in a ship block fell out of it" }
        }
    }

    @GameTest(template = TEST_AREA, batch = "cbc_mount_packet", timeoutTicks = 200)
    fun cannonMountSettingFromNearbyPlayerIsApplied(helper: GameTestHelper) {
        helper.requireMod("createbigcannons")
        helper.withShip(mapOf(BlockPos.ZERO to block("createbigcannons:fixed_cannon_mount")), helper.absolutePos(shipAt)) { ship ->
            val pos = ship.shipPos(0, 0, 0)
            val player = helper.mockPlayerAt(ship.worldCenter(pos).add(2.0, -0.5, 0.0))
            try {
                ServerboundSetFixedCannonMountValuePacket(pos, 1, 5, null, null, null, false, true)
                    .handle(Executor { it.run() }, player.connection, player)
                val mount = helper.level.getBlockEntity(pos) as FixedCannonMountBlockEntity
                val pitch = mount.getBehaviour(FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour.PITCH_TYPE)
                helper.check(pitch.getValue() == 5) { "cannon mount pitch set by a player 2 blocks away wasn't applied (got ${pitch.getValue()})" }
            } finally {
                player.removeMock()
            }
        }
    }
}
