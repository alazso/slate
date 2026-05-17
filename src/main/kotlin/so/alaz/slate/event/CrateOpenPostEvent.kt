package so.alaz.slate.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import so.alaz.slate.crate.CrateDefinition

class CrateOpenPostEvent(
    val player: Player,
    val crate: CrateDefinition,
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
