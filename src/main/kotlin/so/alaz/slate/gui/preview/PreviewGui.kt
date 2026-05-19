package so.alaz.slate.gui.preview

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize
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
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.util.ComponentUtil

/**
 * Preview GUI — opened from `/crates preview <crate>` or from the Crate Detail "Preview Rewards" tile.
 *
 * Implements GUI-REFACTOR.md §3:
 *   - 7×3 reward grid in the inner rows
 *   - Slot 4: crate header info
 *   - Slot 45: sort cycle (4 modes: weight, chance low→high, tier, alphabetical)
 *   - Slot 46: tier filter cycle with "All" + per-tier rotation (overflow handled by single cycling button)
 *   - Slots 48/49/50: prev/page/next
 *   - Slot 53: back
 */
class PreviewGui(
    private val crate: CrateDefinition,
    private val backFactory: ((Player) -> SlateGui)? = null,
    initialTierFilter: String? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_PREVIEW_TITLE, mapOf("name" to ComponentUtil.stripTags(crate.name))),
    rows = 6,
) {
    private var page: Int = 0
    private var sortMode: SortMode = SortMode.BY_WEIGHT
    private var tierFilter: String? = initialTierFilter
    private val orderedTiers: List<String?> = listOf<String?>(null) + crate.tiers.keys.sorted()
    private var tierFilterIndex: Int = orderedTiers.indexOfFirst { it == initialTierFilter }.coerceAtLeast(0)

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank(Material.BLACK_STAINED_GLASS_PANE))
        fillBorder(border)

        val sorted = sortPrizes(crate.prizes, sortMode)
        val filtered = tierFilter?.let { tier -> sorted.filter { it.tier == tier } } ?: sorted
        val totalPages = ((filtered.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        val pageItems = filtered.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
        for ((index, prize) in pageItems.withIndex()) {
            slot(GRID_SLOTS[index], rewardTile(crate, prize))
        }
        for (index in pageItems.size until GRID_SLOTS.size) {
            slot(GRID_SLOTS[index], StaticSlot(blank(Material.BLACK_STAINED_GLASS_PANE)))
        }

        slot(4, headerTile(filtered.size))
        slot(45, sortTile())
        slot(46, filterTile())
        slot(48, prevTile(totalPages))
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

    private fun blank(material: Material): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.empty()) }
        return item
    }

    private fun headerTile(count: Int): StaticSlot {
        val item = ItemStack(crate.keyDefinition.material).clone().apply {
            type = runCatching { crate.keyDefinition.material }.getOrDefault(Material.CHEST)
        }
        val placeholders = mapOf(
            "name" to crate.name,
            "mode" to crate.selection.mode.name,
            "structure" to crate.selection.structure.name,
            "count" to count,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PREVIEW_HEADER_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_PREVIEW_HEADER_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun rewardTile(crate: CrateDefinition, prize: Prize): StaticSlot {
        val stack = prize.items.firstOrNull()
            ?.let { ItemFactory.visualStackForPrizeItem(it, Material.CHEST) }
            ?: ItemStack(Material.PAPER)
        val table = ProbabilityEngine().buildTable(crate)
        val row = table.rows.firstOrNull { it.prize.id == prize.id }
        val ratio = row?.effectiveChance?.takeIf { it > 0.0 }?.let { 1.0 / it }
        val placeholders = mutableMapOf<String, Any?>(
            "chance" to (row?.effectiveChance?.let(ChanceFormatter::percent) ?: "—"),
            "weight" to prize.weight,
            "ratio" to (ratio?.let { String.format(java.util.Locale.ROOT, "1 in %,.0f", it) } ?: "—"),
            "tier" to (prize.tier ?: "—"),
        )
        stack.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(prize.name))
            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            lore += GuiText.lore(MessageKeys.GUI_PREVIEW_REWARD_LORE_BASE, placeholders)
            if (prize.tier != null) lore += GuiText.lore(MessageKeys.GUI_PREVIEW_REWARD_LORE_TIER, placeholders)
            val pity = crate.pityConfig
            if (pity?.enabled == true && prize.tier == pity.guaranteedTier) {
                lore += GuiText.lore(MessageKeys.GUI_PREVIEW_REWARD_LORE_PITY, placeholders + ("requiredOpens" to pity.opensRequired))
            }
            if (prize.broadcast) lore += GuiText.lore(MessageKeys.GUI_PREVIEW_REWARD_LORE_BROADCAST)
            meta.lore(lore)
        }
        return StaticSlot(stack)
    }

    private fun sortTile(): StaticSlot {
        val item = ItemStack(Material.HOPPER)
        val modeLabel = GuiText.raw(sortMode.localeKey)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PREVIEW_SORT_NAME, mapOf("mode" to modeLabel)))
            meta.lore(GuiText.lore(MessageKeys.GUI_PREVIEW_SORT_LORE))
        }
        return StaticSlot(item) { context ->
            sortMode = sortMode.next()
            page = 0
            GuiSounds.click(context.player)
        }
    }

    private fun filterTile(): StaticSlot {
        val tier = orderedTiers.getOrNull(tierFilterIndex)
        tierFilter = tier
        val label = tier ?: GuiText.raw(MessageKeys.GUI_PREVIEW_FILTER_ALL)
        val material = if (tier == null) Material.NETHER_STAR else Material.WHITE_DYE
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PREVIEW_FILTER_NAME, mapOf("tier" to label)))
            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            lore += GuiText.lore(MessageKeys.GUI_PREVIEW_FILTER_LORE)
            if (orderedTiers.size > MAX_DIRECT_TIERS) {
                lore += GuiText.lore(MessageKeys.GUI_PREVIEW_FILTER_OVERFLOW_LORE)
            }
            meta.lore(lore)
            if (tier != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(item) { context ->
            tierFilterIndex = (tierFilterIndex + 1) % orderedTiers.size
            page = 0
            GuiSounds.click(context.player)
        }
    }

    private fun prevTile(totalPages: Int): StaticSlot {
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
            } else {
                GuiSounds.denied(context.player)
            }
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
            } else {
                GuiSounds.denied(context.player)
            }
        }
    }

    private fun pageIndicator(totalPages: Int, total: Int): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf("page" to (page + 1), "total" to totalPages, "count" to total)
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
            if (factory != null) {
                SlateAPI.guiService()?.openGui(context.player, factory(context.player))
            } else {
                context.session.closeRequested = true
            }
        }
    }

    private enum class SortMode(val localeKey: String) {
        BY_WEIGHT(MessageKeys.GUI_PREVIEW_SORT_MODE_WEIGHT),
        BY_CHANCE(MessageKeys.GUI_PREVIEW_SORT_MODE_CHANCE),
        BY_TIER(MessageKeys.GUI_PREVIEW_SORT_MODE_TIER),
        ALPHABETICAL(MessageKeys.GUI_PREVIEW_SORT_MODE_ALPHA),
        ;

        fun next(): SortMode = values()[(ordinal + 1) % values().size]
    }

    private fun sortPrizes(prizes: List<Prize>, mode: SortMode): List<Prize> {
        val byNameThenId = compareBy<Prize>(
            { ComponentUtil.stripTags(it.name).trim().lowercase() },
            { it.id.lowercase() },
        )
        return when (mode) {
            SortMode.BY_WEIGHT -> prizes.sortedWith(compareByDescending<Prize> { it.weight }.then(byNameThenId))
            SortMode.BY_CHANCE -> {
                val table = ProbabilityEngine().buildTable(crate)
                val effective = table.rows.associate { it.prize.id to it.effectiveChance }
                prizes.sortedWith(compareBy<Prize> { effective[it.id] ?: 0.0 }.then(byNameThenId))
            }
            SortMode.BY_TIER -> prizes.sortedWith(
                compareBy<Prize>({ it.tier == null }, { it.tier?.lowercase().orEmpty() }).then(byNameThenId),
            )
            SortMode.ALPHABETICAL -> prizes.sortedWith(byNameThenId)
        }
    }

    companion object {
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )
        private const val MAX_DIRECT_TIERS = 4
    }
}
