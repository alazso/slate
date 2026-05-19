package so.alaz.slate.command

import com.mojang.brigadier.tree.LiteralCommandNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.junit.jupiter.api.Test

class SlateCommandsRegistrationTest {
    @Test
    fun `register wires slate crates keys currency roots and sla alias`() {
        val commands = mockk<Commands>()
        every { commands.register(any<LiteralCommandNode<CommandSourceStack>>(), any(), any()) } returns mutableSetOf()

        SlateCommands.register(commands)

        verify(exactly = 4) { commands.register(any<LiteralCommandNode<CommandSourceStack>>(), any(), any()) }
        verify(exactly = 1) { commands.register(match { it.literal == "slate" }, any(), listOf("sla")) }
        verify(exactly = 1) { commands.register(match { it.literal == "crates" }, any(), emptyList()) }
        verify(exactly = 1) { commands.register(match { it.literal == "keys" }, any(), emptyList()) }
        verify(exactly = 1) { commands.register(match { it.literal == "currency" }, any(), emptyList()) }
        verify(exactly = 0) { commands.register(any<LiteralCommandNode<CommandSourceStack>>(), any(), listOf("sl")) }
        verify(exactly = 0) { commands.register(any<LiteralCommandNode<CommandSourceStack>>(), any(), listOf("cc")) }
    }
}
