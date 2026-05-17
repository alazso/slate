package so.alaz.slate.command.sub

import io.mockk.every
import io.mockk.mockk
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandPermissionsTest {
    @Test
    fun `open command requires open permission`() {
        val source = sourceWith("slate.player.open")
        assertTrue(OpenCommand.node().requirement.test(source))
        assertFalse(OpenCommand.node().requirement.test(sourceWith()))
    }

    @Test
    fun `preview command requires preview permission`() {
        val source = sourceWith("slate.player.preview")
        assertTrue(PreviewCommand.node().requirement.test(source))
        assertFalse(PreviewCommand.node().requirement.test(sourceWith()))
    }

    @Test
    fun `list command requires list permission`() {
        val source = sourceWith("slate.player.list")
        assertTrue(ListCommand.node().requirement.test(source))
        assertFalse(ListCommand.node().requirement.test(sourceWith()))
    }

    @Test
    fun `reload command requires reload permission`() {
        val source = sourceWith("slate.admin.reload")
        assertTrue(ReloadCommand.node().requirement.test(source))
        assertFalse(ReloadCommand.node().requirement.test(sourceWith()))
    }

    @Test
    fun `key subcommands require singular admin key permissions`() {
        val root = KeyCommand.node()
        assertTrue(root.getChild("give").requirement.test(sourceWith("slate.admin.key.give")))
        assertFalse(root.getChild("give").requirement.test(sourceWith()))

        assertTrue(root.getChild("take").requirement.test(sourceWith("slate.admin.key.take")))
        assertFalse(root.getChild("take").requirement.test(sourceWith()))

        assertTrue(root.getChild("set").requirement.test(sourceWith("slate.admin.key.set")))
        assertFalse(root.getChild("set").requirement.test(sourceWith()))
    }

    @Test
    fun `debug quick-hit commands require granular debug permissions`() {
        val root = DebugCommand.node()
        val crate = root.getChild("crate")
        val player = root.getChild("player")
        val eligibility = root.getChild("eligibility")
        assertNotNull(crate)
        assertNotNull(player)
        assertNotNull(eligibility)

        assertTrue(crate.requirement.test(sourceWith("slate.admin.debug.crate")))
        assertFalse(crate.requirement.test(sourceWith()))
        assertTrue(player.requirement.test(sourceWith("slate.admin.debug.player")))
        assertFalse(player.requirement.test(sourceWith()))
        assertTrue(eligibility.requirement.test(sourceWith("slate.admin.debug.eligibility")))
        assertFalse(eligibility.requirement.test(sourceWith()))
        assertNotNull(crate.getChild("crate"))
        assertNotNull(eligibility.getChild("crate"))
    }

    @Test
    fun `anchor quick-hit commands require granular anchor permissions`() {
        val root = AnchorCommand.node()
        val teleport = root.getChild("tp")
        val refresh = root.getChild("refresh")
        assertNotNull(teleport)
        assertNotNull(refresh)

        assertTrue(teleport.requirement.test(sourceWith("slate.admin.anchor.tp")))
        assertFalse(teleport.requirement.test(sourceWith()))
        assertTrue(refresh.requirement.test(sourceWith("slate.admin.anchor.refresh")))
        assertFalse(refresh.requirement.test(sourceWith()))
        assertNotNull(teleport.getChild("crate"))
    }

    private fun sourceWith(vararg permissions: String): CommandSourceStack {
        val sender = mockk<CommandSender>()
        every { sender.hasPermission(any<String>()) } answers { permissions.contains(firstArg<String>()) }
        val source = mockk<CommandSourceStack>()
        every { source.sender } returns sender
        return source
    }
}
