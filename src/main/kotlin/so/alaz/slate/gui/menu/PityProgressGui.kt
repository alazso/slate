package so.alaz.slate.gui.menu

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
import so.alaz.slate.item.visualStackForCrateKey
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.player.PityProgressEntry

/**
 * Pity Progress menu — opened from the Main Menu pity button.
 *
 * Implements GUI-REFACTOR.md §6:
 *   - Slot 4: info book explaining what pity is
 *   - Slots 10..25: per-crate pity tiles with ASCII progress bar, stack-size = current count, glint when near guarantee
 *   - Slot 31: back arrow
 */
class PityProgressGui(
    private val owner: Player,
    private val backFactory: ((Player) -> SlateGui)? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_PITY_TITLE),
    rows = 4,
) {
    private var page: Int = 0

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        slot(4, infoTile())

        val query = SlateAPI.playerCrateQueryService() ?: return
        val entries = query.pityProgress(player)
        if (entries.isEmpty()) {
            slot(22, emptyTile())
            slot(31, backTile())
            return
        }
        val totalPages = ((entries.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0
        val pageEntries = entries.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
        for ((index, entry) in pageEntries.withIndex()) {
            slot(GRID_SLOTS[index], entryTile(entry))
        }
        for (index in pageEntries.size until GRID_SLOTS.size) {
            slot(GRID_SLOTS[index], StaticSlot(blank()))
        }

        slot(29, prevTile())
        slot(31, backTile())
        slot(33, nextTile(totalPages))
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        rebuild(context.player)
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

    private fun infoTile(): StaticSlot {
        val item = ItemStack(Material.WRITTEN_BOOK)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PITY_INFO_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_PITY_INFO_LORE))
        }
        return StaticSlot(item)
    }

    private fun entryTile(entry: PityProgressEntry): StaticSlot {
        val stack = ItemFactory.visualStackForCrateKey(entry.crate, entry.crate.keyDefinition.material)
        stack.amount = entry.current.coerceIn(1, 64)
        val progress = progressBar(entry.current, entry.required)
        val placeholders = mapOf(
            "name" to entry.crate.name,
            "current" to entry.current,
            "required" to entry.required,
            "remaining" to entry.remaining,
            "guaranteedTier" to entry.guaranteedTier,
            "progressBar" to progress,
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PITY_ENTRY_NAME, placeholders))
            val lore = mutableListOf<net.kyori.adventure.text.Component>()
            lore += GuiText.lore(MessageKeys.GUI_PITY_ENTRY_LORE, placeholders)
            if (entry.isNearGuarantee) {
                lore += GuiText.lore(MessageKeys.GUI_PITY_ENTRY_LORE_NEAR, placeholders)
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
            meta.lore(lore)
        }
        return StaticSlot(stack) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(
                context.player,
                CrateDetailGui(context.player, entry.crate.id) { p ->
                    backFactory?.invoke(p) ?: PityProgressGui(p, backFactory)
                },
            )
        }
    }

    private fun emptyTile(): StaticSlot {
        val item = ItemStack(Material.GRAY_DYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_PITY_EMPTY_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_PITY_EMPTY_LORE))
        }
        return StaticSlot(item)
    }

    private fun progressBar(current: Int, required: Int): String {
        if (required <= 0) return "[" + "░".repeat(BAR_SEGMENTS) + "] —"
        val ratio = (current.toDouble() / required).coerceIn(0.0, 1.0)
        val filled = (ratio * BAR_SEGMENTS).toInt().coerceIn(0, BAR_SEGMENTS)
        val empty = BAR_SEGMENTS - filled
        return "[${"█".repeat(filled)}${"░".repeat(empty)}]"
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

    companion object {
        private const val BAR_SEGMENTS = 16
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
        )
    }
}
