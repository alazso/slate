package so.alaz.slate.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import so.alaz.slate.display.DisplayService

class DisplayLifecycleListener(
    private val displayService: DisplayService,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val location = event.player.location
        displayService.reconcileChunk(
            world = event.player.world,
            chunkX = location.chunk.x,
            chunkZ = location.chunk.z,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val location = event.player.location
        displayService.reconcileChunk(
            world = event.player.world,
            chunkX = location.chunk.x,
            chunkZ = location.chunk.z,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        displayService.reconcileChunk(
            world = event.world,
            chunkX = event.chunk.x,
            chunkZ = event.chunk.z,
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldLoad(event: WorldLoadEvent) {
        displayService.reconcileWorld(event.world)
    }
}
