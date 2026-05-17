package so.alaz.slate.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.key.KeyType

class CrateOpenPreEvent(
    val player: Player,
    val crate: CrateDefinition,
    val keyType: KeyType,
) : Event(), Cancellable {
    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
