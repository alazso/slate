package so.alaz.slate.gui.editor

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.UUID
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CrateProbabilityCommand
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.gui.GuiService

class EditorGuiNavigationTest {
    @AfterEach
    fun tearDown() {
        unmockkObject(SlateAPI)
        unmockkObject(EditorCrateAccess)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `crate list click opens crate editor gui`() = runTest {
        val player = mockPlayer()
        val guiService = mockk<GuiService>()
        mockkObject(SlateAPI)
        every { SlateAPI.guiService() } returns guiService
        every { SlateAPI.pluginScope() } returns this
        coEvery { guiService.openGui(player, any()) } returns Unit

        val result = invokeOpenEditor(player, null)
        advanceUntilIdle()

        assertEquals(1, result)
        coVerify { guiService.openGui(player, match { it is CrateListGui }) }
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `crate editor command opens crate-specific editor gui`() = runTest {
        val player = mockPlayer()
        val guiService = mockk<GuiService>()
        val crate = crate("alpha")
        mockkObject(SlateAPI)
        mockkObject(EditorCrateAccess)
        every { SlateAPI.guiService() } returns guiService
        every { SlateAPI.pluginScope() } returns this
        every { EditorCrateAccess.findCrate("alpha") } returns crate
        coEvery { guiService.openGui(player, any()) } returns Unit

        val result = invokeOpenEditor(player, "alpha")
        advanceUntilIdle()

        assertEquals(1, result)
        coVerify { guiService.openGui(player, match { it is CrateEditorGui }) }
    }

    @Test
    fun `crate editor command returns error for missing crate`() = runTest {
        val player = mockPlayer()
        mockkObject(SlateAPI)
        mockkObject(EditorCrateAccess)
        every { SlateAPI.guiService() } returns mockk(relaxed = true)
        every { SlateAPI.pluginScope() } returns this
        every { EditorCrateAccess.findCrate("missing") } returns null

        val result = invokeOpenEditor(player, "missing")
        assertEquals(0, result)
    }

    private fun mockPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns UUID.randomUUID()
        return player
    }

    private fun invokeOpenEditor(player: Player, crateId: String?): Int {
        val source = mockk<io.papermc.paper.command.brigadier.CommandSourceStack>()
        every { source.sender } returns player
        val context = mockk<com.mojang.brigadier.context.CommandContext<io.papermc.paper.command.brigadier.CommandSourceStack>>()
        every { context.source } returns source

        val method = CrateProbabilityCommand::class.java.getDeclaredMethod(
            "openEditor",
            com.mojang.brigadier.context.CommandContext::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(CrateProbabilityCommand, context, crateId) as Int
    }

    private fun crate(id: String): CrateDefinition =
        CrateDefinition(
            id = id,
            name = id,
            keyDefinition = KeyDefinition(
                name = "$id key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            prizes = listOf(
                Prize(
                    id = "reward",
                    name = "Reward",
                    tier = null,
                    weight = 1,
                    chance = null,
                    broadcast = false,
                    broadcastMessage = "",
                    items = listOf(PrizeItem(material = "DIAMOND")),
                    commands = emptyList(),
                    messages = emptyList(),
                ),
            ),
        )
}
