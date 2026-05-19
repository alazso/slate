package so.alaz.slate.gui

import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.util.ComponentUtil

abstract class SlateGui(
    val title: String,
    val rows: Int,
) {
    init {
        require(rows in 1..6) { "rows must be between 1 and 6" }
    }

    protected val size: Int get() = rows * 9
    private val slotMap: Array<GuiSlot?> = arrayOfNulls(size)

    suspend fun open(player: Player, scheduler: PlatformScheduler): GuiSession {
        onBeforeOpen(player)
        val holder = GuiInventoryHolder()
        val inventory = runGlobal(scheduler) {
            Bukkit.createInventory(holder, size, resolveTitle())
        }
        holder.inventoryRef = inventory
        val session = GuiSession(playerId = player.uniqueId, gui = this, inventory = inventory)
        fillInventory(player, inventory, scheduler)
        runGlobal(scheduler) { player.openInventory(inventory) }
        onAfterOpen(player, session)
        return session
    }

    open suspend fun onBeforeOpen(player: Player) = Unit

    open suspend fun onAfterOpen(player: Player, session: GuiSession) = Unit

    open suspend fun onClose(player: Player, session: GuiSession) = Unit

    open suspend fun onClick(
        context: GuiClickContext,
        session: GuiSession,
        slot: GuiSlot?,
    ) {
        slot?.onClick(
            context,
        )
    }

    fun slot(index: Int, slot: GuiSlot?) {
        require(index in 0 until size) { "slot index $index is out of bounds for size $size" }
        slotMap[index] = slot
    }

    fun slot(x: Int, y: Int, slot: GuiSlot?) {
        require(x in 0..8) { "x must be between 0 and 8" }
        require(y in 0 until rows) { "y must be between 0 and ${rows - 1}" }
        slot((y * 9) + x, slot)
    }

    fun region(xs: IntRange, ys: IntRange, slotFactory: (index: Int) -> GuiSlot?) {
        ys.forEach { y ->
            xs.forEach { x ->
                slot(x, y, slotFactory((y * 9) + x))
            }
        }
    }

    protected fun slots(): Array<GuiSlot?> = slotMap.copyOf()

    protected fun fillEmptySlots(slotFactory: (index: Int) -> GuiSlot?) {
        val snapshot = slots()
        snapshot.forEachIndexed { index, slot ->
            if (slot == null) {
                this.slot(index, slotFactory(index))
            }
        }
    }

    suspend fun renderSlot(player: Player, index: Int): org.bukkit.inventory.ItemStack? {
        if (index !in 0 until size) return null
        return slotMap[index]?.render(player)
    }

    suspend fun refresh(player: Player, scheduler: PlatformScheduler, inventory: Inventory) {
        fillInventory(player, inventory, scheduler)
    }

    /**
     * Clears the current slot map and reruns [onBeforeOpen] so the GUI reflects
     * the latest local state and backing data. The framework calls [refresh] after
     * the click pipeline returns, so callers that mutate state inside [onClick]
     * should simply invoke this and let the automatic refresh redraw the inventory.
     */
    protected suspend fun rebuild(player: Player) {
        clearSlots()
        onBeforeOpen(player)
    }

    /**
     * Rebuilds the slot map and immediately pushes the new layout to [inventory].
     * Use this from asynchronous callbacks (post-claim, post-mutation completions)
     * that fire outside the click pipeline and therefore miss the framework's
     * automatic refresh.
     */
    suspend fun rebuildAndRefresh(
        player: Player,
        scheduler: PlatformScheduler,
        inventory: Inventory,
    ) {
        rebuild(player)
        refresh(player, scheduler, inventory)
    }

    protected fun clearSlots() {
        for (index in slotMap.indices) {
            slotMap[index] = null
        }
    }

    suspend fun applyAnimatedFrame(
        player: Player,
        session: GuiSession,
        scheduler: PlatformScheduler,
        step: Int,
    ) {
        slots().forEachIndexed { index, guiSlot ->
            val animated = guiSlot as? AnimatedSlot ?: return@forEachIndexed
            val frame = animated.frameAt(step) ?: return@forEachIndexed
            runGlobal(scheduler) {
                session.inventory.setItem(index, frame)
            }
        }
    }

    internal fun getSlot(index: Int): GuiSlot? = slotMap.getOrNull(index)

    private suspend fun fillInventory(
        player: Player,
        inventory: Inventory,
        scheduler: PlatformScheduler? = null,
    ) {
        val renderedItems = slots().map { guiSlot -> guiSlot?.render(player) }
        if (scheduler == null) {
            renderedItems.forEachIndexed { index, item ->
                val existing = inventory.getItem(index)
                if (!itemsEquivalent(existing, item)) {
                    inventory.setItem(index, item)
                }
            }
        } else {
            runGlobal(scheduler) {
                renderedItems.forEachIndexed { index, item ->
                    val existing = inventory.getItem(index)
                    if (!itemsEquivalent(existing, item)) {
                        inventory.setItem(index, item)
                    }
                }
            }
        }
    }

    private fun itemsEquivalent(
        existing: org.bukkit.inventory.ItemStack?,
        rendered: org.bukkit.inventory.ItemStack?,
    ): Boolean {
        if (existing == null && rendered == null) return true
        if (existing == null || rendered == null) return false
        return existing.amount == rendered.amount && existing.isSimilar(rendered)
    }

    private suspend fun <T> runGlobal(scheduler: PlatformScheduler, block: () -> T): T =
        suspendCancellableCoroutine { continuation ->
            scheduler.runGlobal {
                runCatching { block() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }
        }

    private fun resolveTitle(): net.kyori.adventure.text.Component {
        val stripped = ComponentUtil.stripTags(title)
        val safeText = if (stripped.length > 32) stripped.take(31) + "…" else stripped
        return ComponentUtil.parse(safeText)
    }
}

class GuiInventoryHolder(
    val holderId: UUID = UUID.randomUUID(),
) : InventoryHolder {
    var inventoryRef: Inventory? = null

    override fun getInventory(): Inventory = requireNotNull(inventoryRef) {
        "Inventory not assigned yet for holder $holderId"
    }
}

