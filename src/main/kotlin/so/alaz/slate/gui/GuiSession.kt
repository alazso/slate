package so.alaz.slate.gui

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

data class GuiSession(
    val sessionId: UUID = UUID.randomUUID(),
    val playerId: UUID,
    val gui: SlateGui,
    val inventory: Inventory,
    val openedAtMs: Long = System.currentTimeMillis(),
    var currentPage: Int = 0,
    @Volatile var closeRequested: Boolean = false,
) {
    private val lastClickMs = AtomicLong(0L)

    fun passesDebounce(cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastClickMs.get()
        if (now - previous < cooldownMs) return false
        lastClickMs.set(now)
        return true
    }

    fun isOwner(player: Player): Boolean = player.uniqueId == playerId
}

