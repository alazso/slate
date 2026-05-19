package so.alaz.slate.gui.menu

import java.time.Duration
import java.time.Instant
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.visualStackForPrizeItem
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.key.KeyType
import so.alaz.slate.storage.OpenHistoryEntry

/**
 * Open History menu — opened from the Main Menu "Open History" button or `/crates history`.
 *
 * Implements GUI-REFACTOR.md §5:
 *   - Slots 10..34: history entries (newest first), rendered using the prize's actual item
 *   - Slot 45: crate filter cycle (All Crates / each crate that appears in history)
 *   - Slots 48/49/50: prev/page/next
 *   - Slot 53: back
 */
class OpenHistoryGui(
    private val owner: Player,
    private val backFactory: ((Player) -> SlateGui)? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_HISTORY_TITLE),
    rows = 6,
) {
    private var page: Int = 0
    private var filterIndex: Int = 0
    private var filterCrates: List<String> = listOf()

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val query = SlateAPI.playerCrateQueryService() ?: return
        val historyAll = query.openHistory(player, MAX_HISTORY, crateIdFilter = null)
        filterCrates = historyAll.map { it.crateId }.distinct().sorted()
        val crateFilter = filterCrates.getOrNull(filterIndex - 1)
        val filtered = if (crateFilter == null) historyAll else historyAll.filter { it.crateId == crateFilter }
        val totalPages = ((filtered.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        if (filtered.isEmpty()) {
            slot(22, emptyTile())
        } else {
            val pageEntries = filtered.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
            for ((index, entry) in pageEntries.withIndex()) {
                slot(GRID_SLOTS[index], entryTile(entry))
            }
            for (index in pageEntries.size until GRID_SLOTS.size) {
                slot(GRID_SLOTS[index], StaticSlot(blank()))
            }
        }

        slot(45, filterTile(crateFilter))
        slot(48, prevTile())
        slot(49, pageIndicator(totalPages, filtered.size))
        slot(50, nextTile(totalPages))
        slot(53, backTile())
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        val scheduler = SlateAPI.scheduler() ?: return
        refresh(context.player, scheduler, session.inventory)
    }

    private fun fillBorder(border: StaticSlot) {
        val sz = rows * 9
        for (i in 0 until 9) slot(i, border)
        for (i in (sz - 9) until sz) slot(i, border)
        for (y in 1 until rows - 1) {
            slot(y * 9, border)
            slot((y * 9) + 8, border)
        }
    }

    private fun blank(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.empty()) }
        return item
    }

    private fun entryTile(entry: OpenHistoryEntry): StaticSlot {
        val crateManager = SlateAPI.crateManager()
        val crate = crateManager?.byId(entry.crateId)
        val prize = crate?.prizes?.firstOrNull { it.id == entry.prizeId }
        val stack = prize?.items?.firstOrNull()
            ?.let { ItemFactory.visualStackForPrizeItem(it, Material.PAPER) }
            ?: ItemStack(Material.PAPER)
        val placeholders = mapOf(
            "prize" to entry.prizeName,
            "crate" to (crate?.name ?: entry.crateId),
            "tier" to (prize?.tier ?: "—"),
            "keyType" to keyTypeLabel(entry.keyType),
            "when" to relativeTime(entry.openedAt),
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_HISTORY_ENTRY_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_HISTORY_ENTRY_LORE, placeholders))
        }
        return StaticSlot(stack)
    }

    private fun filterTile(currentFilter: String?): StaticSlot {
        val item = ItemStack(Material.CHEST)
        val label = currentFilter ?: GuiText.raw(MessageKeys.GUI_HISTORY_FILTER_ALL)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_HISTORY_FILTER_NAME, mapOf("crate" to label)))
            meta.lore(GuiText.lore(MessageKeys.GUI_HISTORY_FILTER_LORE))
        }
        return StaticSlot(item) { context ->
            filterIndex = (filterIndex + 1) % (filterCrates.size + 1)
            page = 0
            GuiSounds.click(context.player)
        }
    }

    private fun emptyTile(): StaticSlot {
        val item = ItemStack(Material.PAPER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_HISTORY_EMPTY_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_HISTORY_EMPTY_LORE))
        }
        return StaticSlot(item)
    }

    private fun prevTile(): StaticSlot {
        val enabled = page > 0
        val item = ItemStack(if (enabled) Material.ARROW else Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PREV_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PREV_LORE))
        }
        return StaticSlot(item) { context ->
            if (enabled) {
                page--
                GuiSounds.pageTurn(context.player)
            } else GuiSounds.denied(context.player)
        }
    }

    private fun nextTile(totalPages: Int): StaticSlot {
        val enabled = page < totalPages - 1
        val item = ItemStack(if (enabled) Material.ARROW else Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_NEXT_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_NEXT_LORE))
        }
        return StaticSlot(item) { context ->
            if (enabled) {
                page++
                GuiSounds.pageTurn(context.player)
            } else GuiSounds.denied(context.player)
        }
    }

    private fun pageIndicator(totalPages: Int, count: Int): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf("page" to (page + 1), "total" to totalPages, "count" to count)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun backTile(): StaticSlot {
        val factory = backFactory
        val item = ItemStack(if (factory != null) Material.ARROW else Material.BARRIER)
        val (nameKey, loreKey) = if (factory != null) {
            MessageKeys.GUI_CONTROL_BACK_NAME to MessageKeys.GUI_CONTROL_BACK_LORE
        } else {
            MessageKeys.GUI_CONTROL_CLOSE_NAME to MessageKeys.GUI_CONTROL_CLOSE_LORE
        }
        item.editMeta { meta ->
            meta.displayName(GuiText.component(nameKey))
            meta.lore(GuiText.lore(loreKey))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            if (factory != null) SlateAPI.guiService()?.openGui(context.player, factory(context.player))
            else context.session.closeRequested = true
        }
    }

    private fun keyTypeLabel(type: KeyType): String = when (type) {
        KeyType.Physical -> "Physical"
        KeyType.Virtual -> "Virtual"
    }

    private fun relativeTime(instant: Instant): String {
        val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0L)
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            seconds < 2_592_000 -> "${seconds / 86_400}d ago"
            else -> "${seconds / 2_592_000}mo ago"
        }
    }

    companion object {
        private const val MAX_HISTORY = 200
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
    }
}
