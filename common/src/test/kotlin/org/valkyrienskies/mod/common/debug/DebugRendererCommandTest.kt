package org.valkyrienskies.mod.common.debug

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.mockk.every
import io.mockk.mockk
import net.minecraft.commands.CommandSourceStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.common.command.commands.DebugCommand

class DebugRendererCommandTest {
    private fun dispatcher(): CommandDispatcher<CommandSourceStack> {
        val root = LiteralArgumentBuilder.literal<CommandSourceStack>("vs")
        DebugCommand.register(root)
        return CommandDispatcher<CommandSourceStack>().also { it.register(root) }
    }

    @Test
    fun `completion offers the four modes and old options are rejected`() {
        val dispatcher = dispatcher()
        val source = mockk<CommandSourceStack>()
        every { source.hasPermission(2) } returns true
        val completions = dispatcher.getCompletionSuggestions(dispatcher.parse("vs debug renderer ", source)).get()
        assertEquals(setOf("none", "markers", "text", "full"), completions.list.map { it.text }.toSet())
        for (mode in listOf("none", "markers", "text", "full")) {
            val parsed = dispatcher.parse("vs debug renderer $mode", source)
            assertTrue(parsed.exceptions.isEmpty())
            assertFalse(parsed.reader.canRead())
        }
        for (removed in listOf("on", "off", "test")) {
            assertThrows(CommandSyntaxException::class.java) {
                dispatcher.execute("vs debug renderer $removed", source)
            }
        }
    }

    @Test
    fun `a player without permission cannot use the debug command`() {
        val dispatcher = dispatcher()
        val source = mockk<CommandSourceStack>()
        every { source.hasPermission(2) } returns false
        assertThrows(CommandSyntaxException::class.java) {
            dispatcher.execute("vs debug renderer full", source)
        }
    }
}
