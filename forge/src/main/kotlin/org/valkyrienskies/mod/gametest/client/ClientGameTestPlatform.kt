package org.valkyrienskies.mod.gametest.client

import com.mojang.blaze3d.vertex.PoseStack
import com.simibubi.create.content.trains.entity.TrainRelocator
import li.cil.tis3d.client.renderer.RenderContextImpl
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID

// Forge side of the shared client tests. Run with DISPLAY set: ./gradlew -PvsTestMods :forge:runGameTestClient

@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object ForgeClientTestHook {
    @SubscribeEvent
    @JvmStatic
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) ClientShipTestRunner.tick(Minecraft.getInstance())
    }
}

fun clickTrainRelocator(mc: Minecraft) =
    TrainRelocator.onClicked(InputEvent.InteractionKeyMappingTriggered(1, mc.options.keyUse, InteractionHand.MAIN_HAND))

/** Forge-only mods (needs -PvsTestMods). */
fun platformClientTests(): List<ClientShipTest> = listOf(
    ClientShipTest("TIS-3D shows casing details for a ship block near the camera", mapOf(BlockPos.ZERO to Blocks.STONE.defaultBlockState()), BlockPos(3, 1, 0)) { mc, ship, _ ->
        clientCheck(ModList.get().isLoaded("tis3d")) { "TIS-3D isn't loaded; run with -PvsTestMods" }
        val context = RenderContextImpl(mc.blockEntityRenderDispatcher, PoseStack(), mc.renderBuffers().bufferSource(), 1f, 0, 0)
        clientCheck(context.closeEnoughForDetails(ship.shipPos(0, 0, 0))) { "TIS-3D treated a ship block 3 blocks from the camera as too far for details" }
    }
)
