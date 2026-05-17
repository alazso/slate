package so.alaz.slate.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.scheduler.PlatformScheduler

abstract class PaginatedGui(
    title: String,
    rows: Int,
    private val contentSlots: List<Int>,
    private val prevSlot: Int,
    private val nextSlot: Int,
    private val closeSlot: Int,
    private val indicatorSlot: Int,
) : SlateGui(title, rows) {
    init {
        require(contentSlots.isNotEmpty()) { "contentSlots must not be empty" }
    }

    protected abstract suspend fun loadPage(player: Player, page: Int): List<ItemStack>

    protected open fun maxPage(itemsOnPage: Int): Int = if (itemsOnPage <= 0) 0 else Int.MAX_VALUE

    protected open fun previousItem(player: Player): ItemStack = org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW)
    protected open fun nextItem(player: Player): ItemStack = org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW)
    protected open fun closeItem(player: Player): ItemStack = org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER)
    protected open fun indicatorItem(player: Player, page: Int): ItemStack = org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER)

    override suspend fun onBeforeOpen(player: Player) {
        setNavigationSlots(player, 0)
        renderPage(player, 0)
    }

    override suspend fun onAfterOpen(player: Player, session: GuiSession) {
        session.currentPage = 0
    }

    override suspend fun onClick(
        context: GuiClickContext,
        session: GuiSession,
        slot: GuiSlot?,
    ) {
        val player = context.player
        when (context.slotIndex) {
            prevSlot -> {
                val target = (session.currentPage - 1).coerceAtLeast(0)
                goToPage(player, session, target)
            }

            nextSlot -> {
                val target = session.currentPage + 1
                goToPage(player, session, target)
            }

            closeSlot -> {
                player.closeInventory()
            }

            else -> super.onClick(context, session, slot)
        }
    }

    suspend fun goToPage(player: Player, session: GuiSession, page: Int) {
        val safePage = page.coerceAtLeast(0)
        renderPage(player, safePage)
        setNavigationSlots(player, safePage)
        session.currentPage = safePage
    }

    protected suspend fun renderPage(player: Player, page: Int) {
        val pageItems = loadPage(player, page)
        contentSlots.forEachIndexed { index, slotIndex ->
            val item = pageItems.getOrNull(index)
            slot(slotIndex, item?.let { PaginatedSlot(it) })
        }
    }

    protected fun setNavigationSlots(player: Player, page: Int) {
        slot(prevSlot, StaticSlot(previousItem(player)))
        slot(nextSlot, StaticSlot(nextItem(player)))
        slot(closeSlot, StaticSlot(closeItem(player)) { it.player.closeInventory() })
        slot(indicatorSlot, StaticSlot(indicatorItem(player, page)))
    }
}

suspend fun PaginatedGui.refreshPage(
    player: Player,
    session: GuiSession,
    scheduler: PlatformScheduler,
) {
    goToPage(player, session, session.currentPage)
    refresh(player, scheduler, session.inventory)
}

