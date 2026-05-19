package so.alaz.slate.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.util.ComponentUtil

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

    protected open fun previousItem(player: Player, enabled: Boolean): ItemStack =
        ItemStack(if (enabled) org.bukkit.Material.ARROW else org.bukkit.Material.GRAY_DYE).apply {
            editMeta { meta ->
                meta.displayName(ComponentUtil.parse(if (enabled) "<yellow>Previous Page</yellow>" else "<dark_gray>Previous Page</dark_gray>"))
            }
        }

    protected open fun nextItem(player: Player, enabled: Boolean): ItemStack =
        ItemStack(if (enabled) org.bukkit.Material.ARROW else org.bukkit.Material.GRAY_DYE).apply {
            editMeta { meta ->
                meta.displayName(ComponentUtil.parse(if (enabled) "<yellow>Next Page</yellow>" else "<dark_gray>Next Page</dark_gray>"))
            }
        }

    protected open fun closeItem(player: Player): ItemStack =
        ItemStack(org.bukkit.Material.BARRIER).apply {
            editMeta { meta ->
                meta.displayName(ComponentUtil.parse("<red>Close</red>"))
            }
        }

    protected open fun indicatorItem(player: Player, page: Int): ItemStack =
        ItemStack(org.bukkit.Material.PAPER).apply {
            editMeta { meta ->
                meta.displayName(ComponentUtil.parse("<gold>Page ${page + 1}</gold>"))
            }
        }

    override suspend fun onBeforeOpen(player: Player) {
        val hasNext = loadPage(player, 1).isNotEmpty()
        setNavigationSlots(player, 0, hasPrevious = false, hasNext = hasNext)
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
                if (session.currentPage <= 0) return
                val target = (session.currentPage - 1).coerceAtLeast(0)
                goToPage(player, session, target)
            }

            nextSlot -> {
                val target = session.currentPage + 1
                if (loadPage(player, target).isEmpty()) return
                goToPage(player, session, target)
            }

            closeSlot -> {
                session.closeRequested = true
            }

            else -> super.onClick(context, session, slot)
        }
    }

    suspend fun goToPage(player: Player, session: GuiSession, page: Int) {
        val safePage = page.coerceAtLeast(0)
        renderPage(player, safePage)
        val hasPrevious = safePage > 0
        val hasNext = loadPage(player, safePage + 1).isNotEmpty()
        setNavigationSlots(player, safePage, hasPrevious = hasPrevious, hasNext = hasNext)
        session.currentPage = safePage
    }

    protected suspend fun renderPage(player: Player, page: Int) {
        val pageItems = loadPage(player, page)
        contentSlots.forEachIndexed { index, slotIndex ->
            val item = pageItems.getOrNull(index)
            slot(slotIndex, item?.let { PaginatedSlot(it) })
        }
    }

    protected fun setNavigationSlots(player: Player, page: Int, hasPrevious: Boolean, hasNext: Boolean) {
        slot(prevSlot, StaticSlot(previousItem(player, hasPrevious)))
        slot(nextSlot, StaticSlot(nextItem(player, hasNext)))
        slot(closeSlot, StaticSlot(closeItem(player)) { it.session.closeRequested = true })
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

