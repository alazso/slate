package so.alaz.slate.gui.menu

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch
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
import so.alaz.slate.storage.PendingPrize

/**
 * Pending Claims menu — opened from the Main Menu Claims button.
 *
 * Lists every pending prize with its real visual stack and offers a single "Claim All" action that
 * defers to `ClaimService`. After a successful claim the menu refreshes to reflect new state.
 */
class PendingClaimsGui(
    private val owner: Player,
    private val backFactory: ((Player) -> SlateGui)? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_PENDING_TITLE),
    rows = 6,
) {
    private var page: Int = 0

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val query = SlateAPI.playerCrateQueryService() ?: return
        val pending = query.pendingPrizes(player)
        val totalPages = ((pending.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        if (pending.isEmpty()) {
            slot(22, emptyTile())
        } else {
            val pageItems = pending.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
            for ((index, pendingPrize) in pageItems.withIndex()) {
                slot(GRID_SLOTS[index], entryTile(pendingPrize))
            }
            for (index in pageItems.size until GRID_SLOTS.size) {
                slot(GRID_SLOTS[index], StaticSlot(blank()))
            }
        }

        slot(45, claimAllTile(pending.size))
        slot(48, prevTile())
        slot(49, pageIndicator(totalPages, pending.size))
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

    private fun entryTile(pendingPrize: PendingPrize): StaticSlot {
        val crateManager = SlateAPI.crateManager()
        val crate = crateManager?.byId(pendingPrize.crateId)
        val prize = crate?.prizes?.firstOrNull { it.id == pendingPrize.prizeId }
        val stack = prize?.items?.firstOrNull()
            ?.let { ItemFactory.visualStackForPrizeItem(it, Material.CHEST) }
            ?: ItemStack(Material.CHEST)
        val placeholders = mapOf(
            "prize" to (prize?.name ?: pendingPrize.prizeId),
            "crate" to (crate?.name ?: pendingPrize.crateId),
            "when" to relativeTime(pendingPrize.createdAt),
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PENDING_ENTRY_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_PENDING_ENTRY_LORE, placeholders))
        }
        return StaticSlot(stack)
    }

    private fun claimAllTile(pendingCount: Int): StaticSlot {
        val item = ItemStack(if (pendingCount > 0) Material.HOPPER else Material.GRAY_STAINED_GLASS_PANE)
        val placeholders = mapOf("count" to pendingCount)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PENDING_CLAIM_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_PENDING_CLAIM_LORE, placeholders))
            if (pendingCount > 0) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(item) { context ->
            if (pendingCount <= 0) {
                GuiSounds.denied(context.player)
                return@StaticSlot
            }
            val scope = SlateAPI.pluginScope() ?: return@StaticSlot
            val claimService = SlateAPI.claimService() ?: return@StaticSlot
            GuiSounds.success(context.player)
            scope.launch {
                claimService.claimPending(context.player)
                SlateAPI.scheduler()?.let { sched -> refresh(context.player, sched, context.session.inventory) }
            }
        }
    }

    private fun emptyTile(): StaticSlot {
        val item = ItemStack(Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PENDING_EMPTY_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_PENDING_EMPTY_LORE))
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
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
    }
}
