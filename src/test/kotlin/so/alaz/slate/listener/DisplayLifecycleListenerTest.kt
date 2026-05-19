package so.alaz.slate.listener

import io.mockk.mockk
import io.mockk.verify
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.junit.jupiter.api.Test
import so.alaz.slate.display.DisplayService

class DisplayLifecycleListenerTest {
    @Test
    fun `player join reconciles current chunk`() {
        val displayService = mockk<DisplayService>(relaxed = true)
        val listener = DisplayLifecycleListener(displayService)
        val world = mockk<World>()
        val chunk = mockk<Chunk>()
        val player = mockk<Player>()
        val location = mockk<Location>()
        val event = mockk<PlayerJoinEvent>()
        io.mockk.every { chunk.x } returns 4
        io.mockk.every { chunk.z } returns -2
        io.mockk.every { location.chunk } returns chunk
        io.mockk.every { player.world } returns world
        io.mockk.every { player.location } returns location
        io.mockk.every { event.player } returns player

        listener.onPlayerJoin(event)

        verify(exactly = 1) { displayService.reconcileChunk(world, 4, -2) }
    }

    @Test
    fun `player world change reconciles destination chunk`() {
        val displayService = mockk<DisplayService>(relaxed = true)
        val listener = DisplayLifecycleListener(displayService)
        val world = mockk<World>()
        val chunk = mockk<Chunk>()
        val player = mockk<Player>()
        val location = mockk<Location>()
        val event = mockk<PlayerChangedWorldEvent>()
        io.mockk.every { chunk.x } returns 0
        io.mockk.every { chunk.z } returns 0
        io.mockk.every { location.chunk } returns chunk
        io.mockk.every { player.world } returns world
        io.mockk.every { player.location } returns location
        io.mockk.every { event.player } returns player

        listener.onPlayerChangedWorld(event)

        verify(exactly = 1) { displayService.reconcileChunk(world, 0, 0) }
    }

    @Test
    fun `chunk load reconciles chunk anchors`() {
        val displayService = mockk<DisplayService>(relaxed = true)
        val listener = DisplayLifecycleListener(displayService)
        val world = mockk<World>()
        val chunk = mockk<Chunk>()
        val event = mockk<ChunkLoadEvent>()
        io.mockk.every { chunk.x } returns 8
        io.mockk.every { chunk.z } returns 9
        io.mockk.every { event.world } returns world
        io.mockk.every { event.chunk } returns chunk

        listener.onChunkLoad(event)

        verify(exactly = 1) { displayService.reconcileChunk(world, 8, 9) }
    }

    @Test
    fun `world load reconciles world anchors`() {
        val displayService = mockk<DisplayService>(relaxed = true)
        val listener = DisplayLifecycleListener(displayService)
        val world = mockk<World>()
        val event = mockk<WorldLoadEvent>()
        io.mockk.every { event.world } returns world

        listener.onWorldLoad(event)

        verify(exactly = 1) { displayService.reconcileWorld(world) }
    }
}
