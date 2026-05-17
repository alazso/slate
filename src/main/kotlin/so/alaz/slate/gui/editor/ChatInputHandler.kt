package so.alaz.slate.gui.editor

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.Plugin
import so.alaz.slate.scheduler.PlatformScheduler
import java.time.Duration

class ChatInputHandler(
    private val plugin: Plugin,
    private val scheduler: PlatformScheduler,
) {
    suspend fun awaitChatInput(
        player: Player,
        timeout: Duration = Duration.ofSeconds(30),
    ): String? {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Listener {
                @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                fun onChat(event: AsyncPlayerChatEvent) {
                    if (event.player.uniqueId != player.uniqueId) return
                    event.isCancelled = true
                    val message = event.message.trim()
                    org.bukkit.event.HandlerList.unregisterAll(this)
                    continuation.resume(if (message.equals("cancel", ignoreCase = true)) null else message)
                }
            }
            plugin.server.pluginManager.registerEvents(listener, plugin)
            scheduler.runAsyncDelayed(timeout) {
                if (continuation.isCompleted) return@runAsyncDelayed
                org.bukkit.event.HandlerList.unregisterAll(listener)
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                org.bukkit.event.HandlerList.unregisterAll(listener)
            }
        }
    }
}

