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
import so.alaz.slate.player.KeyWalletEntry

/**
 * Key Wallet — opened from the Main Menu "My Keys" button.
 *
 * Implements GUI-REFACTOR.md §4:
 *   - Slot 4: player head summary
 *   - Slots 10..25: per-crate key entries (stack size = virtual count, enchant glint when openable)
 *   - Slot 26: filter toggle (all crates vs only crates the player holds keys for)
 *   - Slot 27: physical-keys footer note
 *   - Slot 31: back arrow
 */
class KeyWalletGui(
    private val owner: Player,
    private val backFactory: ((Player) -> SlateGui)? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_WALLET_TITLE),
    rows = 4,
) {
    private var page: Int = 0
    private var ownedOnly: Boolean = true

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val query = SlateAPI.playerCrateQueryService() ?: return
        val wallet = query.keyWallet(player)
        val entries = if (ownedOnly) wallet.cratesWithKeys else wallet.entries
        val totalPages = ((entries.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        slot(4, headerTile(player, wallet.totalVirtualKeys, wallet.cratesWithKeys.size))

        val pageEntries = entries.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
        for ((index, entry) in pageEntries.withIndex()) {
            slot(GRID_SLOTS[index], entryTile(entry))
        }
        for (index in pageEntries.size until GRID_SLOTS.size) {
            slot(GRID_SLOTS[index], StaticSlot(blank()))
        }

        slot(26, filterTile())
        slot(27, physicalFooter())
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

    private fun headerTile(player: Player, totalKeys: Int, withKeys: Int): StaticSlot {
        val item = ItemStack(Material.PLAYER_HEAD)
        val placeholders = mapOf("player" to player.name, "totalKeys" to totalKeys, "withKeys" to withKeys)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(player, MessageKeys.GUI_WALLET_HEAD_NAME, placeholders))
            meta.lore(GuiText.lore(player, MessageKeys.GUI_WALLET_HEAD_LORE, placeholders))
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.owningPlayer = player
            }
        }
        return StaticSlot(item)
    }

    private fun entryTile(entry: KeyWalletEntry): StaticSlot {
        val stack = ItemFactory.visualStackForCrateKey(entry.crate, entry.crate.keyDefinition.material)
        stack.amount = entry.virtualKeys.coerceIn(1, 64)
        val placeholders = mapOf(
            "name" to entry.crate.name,
            "amount" to entry.virtualKeys,
            "opensAvailable" to entry.opensAvailable,
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_WALLET_ENTRY_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_WALLET_ENTRY_LORE, placeholders))
            if (entry.opensAvailable > 0) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(stack) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(
                context.player,
                CrateDetailGui(context.player, entry.crate.id) { p ->
                    backFactory?.invoke(p) ?: KeyWalletGui(p, backFactory)
                },
            )
        }
    }

    private fun filterTile(): StaticSlot {
        val item = ItemStack(if (ownedOnly) Material.NETHER_STAR else Material.GRAY_DYE)
        val mode = GuiText.raw(
            if (ownedOnly) MessageKeys.GUI_WALLET_FILTER_OWNED else MessageKeys.GUI_WALLET_FILTER_ALL,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_WALLET_FILTER_NAME, mapOf("mode" to mode)))
            meta.lore(GuiText.lore(MessageKeys.GUI_WALLET_FILTER_LORE))
        }
        return StaticSlot(item) { context ->
            ownedOnly = !ownedOnly
            page = 0
            GuiSounds.click(context.player)
        }
    }

    private fun physicalFooter(): StaticSlot {
        val item = ItemStack(Material.TRIPWIRE_HOOK)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_WALLET_PHYSICAL_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_WALLET_PHYSICAL_LORE))
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
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
        )
    }
}
