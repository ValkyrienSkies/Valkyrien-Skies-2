package org.valkyrienskies.mod.forge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder(ValkyrienSkiesMod.MOD_ID)
public class ShipGameTests {

    /**
     * Tests that a player can spawn a ship by assembling blocks.
     * Places a stone block, assembles it into a ship, and verifies the original block is removed
     * (indicating it was moved to the shipyard).
     */
    @GameTest(template = "empty_platform")
    public void playerSpawnsShip(GameTestHelper helper) {
        // Place a stone block in the test area
        BlockPos placePos = new BlockPos(1, 1, 1);
        helper.setBlock(placePos, Blocks.STONE.defaultBlockState());

        // Verify the block was placed
        helper.assertBlockPresent(Blocks.STONE, placePos);

        // Run at the next tick to allow block placement to settle
        helper.runAfterDelay(1, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos absolutePos = helper.absolutePos(placePos);

            // Assemble the block into a ship
            Set<BlockPos> blocks = new HashSet<>();
            blocks.add(absolutePos);

            try {
                ShipAssembler.assembleToShip(level, blocks, 1.0);
            } catch (Exception e) {
                helper.fail("Failed to assemble ship: " + e.getMessage());
                return;
            }

            // After assembly, the original block should be removed (moved to the shipyard)
            helper.assertBlockPresent(Blocks.AIR, placePos);

            helper.succeed();
        });
    }
}
