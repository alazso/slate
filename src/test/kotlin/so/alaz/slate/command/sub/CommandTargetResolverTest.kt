package so.alaz.slate.command.sub

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandTargetResolverTest {
    @AfterEach
    fun tearDown() {
        unmockkStatic(Bukkit::class)
    }

    @Test
    fun `resolveUuid accepts raw uuid input`() {
        val uuid = UUID.randomUUID()
        assertEquals(uuid, CommandTargetResolver.resolveUuid(uuid.toString()))
    }

    @Test
    fun `resolveUuid uses online player first`() {
        mockkStatic(Bukkit::class)
        val player = mockk<Player>()
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId
        every { Bukkit.getPlayerExact("Alex") } returns player
        every { Bukkit.getOfflinePlayerIfCached("Alex") } returns null

        assertEquals(playerId, CommandTargetResolver.resolveUuid("Alex"))
    }

    @Test
    fun `resolveUuid falls back to cached offline player`() {
        mockkStatic(Bukkit::class)
        val offline = mockk<OfflinePlayer>()
        val playerId = UUID.randomUUID()
        every { Bukkit.getPlayerExact("Steve") } returns null
        every { Bukkit.getOfflinePlayerIfCached("Steve") } returns offline
        every { offline.uniqueId } returns playerId

        assertEquals(playerId, CommandTargetResolver.resolveUuid("Steve"))
    }

    @Test
    fun `resolveUuid returns null when unknown`() {
        mockkStatic(Bukkit::class)
        every { Bukkit.getPlayerExact("Unknown") } returns null
        every { Bukkit.getOfflinePlayerIfCached("Unknown") } returns null

        assertNull(CommandTargetResolver.resolveUuid("Unknown"))
    }
}
