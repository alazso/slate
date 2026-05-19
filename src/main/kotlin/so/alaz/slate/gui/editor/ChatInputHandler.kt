package so.alaz.slate.gui.editor

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.plugin.Plugin
import so.alaz.slate.scheduler.PlatformScheduler
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class ChatInputHandler(
    private val plugin: Plugin,
    private val scheduler: PlatformScheduler,
) {
    suspend fun awaitChatInput(
        player: Player,
        timeout: Duration = Duration.ofSeconds(30),
    ): String? {
        return suspendCancellableCoroutine { continuation ->
            val consumed = AtomicBoolean(false)
            val listener = object : Listener {
                @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                fun onAsyncChat(event: AsyncChatEvent) {
                    if (event.player.uniqueId != player.uniqueId) return
                    event.isCancelled = true
                    val message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
                    complete(message)
                }

                @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
                fun onChat(event: AsyncPlayerChatEvent) {
                    if (event.player.uniqueId != player.uniqueId) return
                    event.isCancelled = true
                    val message = event.message.trim()
                    complete(message)
                }

                private fun complete(message: String) {
                    if (!consumed.compareAndSet(false, true)) return
                    HandlerList.unregisterAll(this)
                    if (!continuation.isCompleted) {
                        continuation.resume(if (message.equals("cancel", ignoreCase = true)) null else message)
                    }
                }
            }
            plugin.server.pluginManager.registerEvents(listener, plugin)
            scheduler.runAsyncDelayed(timeout) {
                if (continuation.isCompleted) return@runAsyncDelayed
                if (!consumed.compareAndSet(false, true)) return@runAsyncDelayed
                HandlerList.unregisterAll(listener)
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                if (!consumed.compareAndSet(false, true)) return@invokeOnCancellation
                HandlerList.unregisterAll(listener)
            }
        }
    }
}

