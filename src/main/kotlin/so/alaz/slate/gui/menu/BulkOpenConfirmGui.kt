package so.alaz.slate.gui.menu

import kotlinx.coroutines.launch
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.OpenPreflightResult

/**
 * Bulk Open confirmation — opened by shift-clicking the Open tile in Crate Detail.
 *
 * Implements GUI-REFACTOR.md §7:
 *   - Row 0 slot 4: crate header with per-open cost + maximum affordable amount
 *   - Row 1: amount buttons (1, 3, 5, 10, MAX) — each pre-flighted via OpenService so blocked amounts grey out
 *   - Row 2 slot 13: back arrow
 *
 * Cost lore is rendered using the live economy formatter so currency display matches chat.
 */
class BulkOpenConfirmGui(
    private val owner: Player,
    private val crateId: String,
    private val backFactory: ((Player) -> SlateGui)? = null,
) : SlateGui(
    title = GuiText.raw(
        MessageKeys.GUI_BULK_TITLE,
        mapOf("name" to (SlateAPI.crateManager()?.byId(crateId)?.name ?: crateId)),
    ),
    rows = 3,
) {
    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val crate = SlateAPI.crateManager()?.byId(crateId) ?: return
        val openService = SlateAPI.openService() ?: return
        val cap = SlateAPI.configManager()?.current?.maxBulkOpenAmount?.coerceAtLeast(1) ?: 10
        val maxAffordable = openService.maxAffordable(player, crateId, cap)

        slot(4, headerTile(crate, maxAffordable))

        for ((index, amount) in DEFAULT_AMOUNTS.withIndex()) {
            val slotIndex = AMOUNT_SLOTS[index]
            val effectiveAmount = amount.coerceAtMost(cap)
            val preflight = openService.preflightById(player, crateId, effectiveAmount)
            slot(slotIndex, amountTile(crate, effectiveAmount, preflight))
        }
        slot(16, maxTile(crate, maxAffordable, cap))
        slot(22, backTile())
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

    private fun headerTile(crate: CrateDefinition, maxAffordable: Int): StaticSlot {
        val item = ItemStack(crate.keyDefinition.material)
        val placeholders = mapOf(
            "name" to crate.name,
            "singleCost" to renderCost(crate, 1),
            "maxAffordable" to maxAffordable,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_BULK_HEAD_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_BULK_HEAD_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun amountTile(crate: CrateDefinition, amount: Int, preflight: OpenPreflightResult?): StaticSlot {
        val ready = preflight is OpenPreflightResult.Ready
        val material = if (ready) Material.LIME_DYE else Material.RED_DYE
        val item = ItemStack(material)
        item.amount = amount.coerceIn(1, 64)
        val placeholders = mapOf(
            "amount" to amount,
            "keys" to crate.requiredKeys * amount,
            "cost" to renderCost(crate, amount),
            "reason" to ((preflight as? OpenPreflightResult.Blocked)
                ?.let { CommandMessages.openFailureReason(it.reason) }.orEmpty()),
        )
        item.editMeta { meta ->
            if (ready) {
                meta.displayName(GuiText.component(MessageKeys.GUI_BULK_AMOUNT_READY_NAME, placeholders))
                meta.lore(GuiText.lore(MessageKeys.GUI_BULK_AMOUNT_READY_LORE, placeholders))
            } else {
                meta.displayName(GuiText.component(MessageKeys.GUI_BULK_AMOUNT_BLOCKED_NAME, placeholders))
                meta.lore(GuiText.lore(MessageKeys.GUI_BULK_AMOUNT_BLOCKED_LORE, placeholders))
            }
        }
        return StaticSlot(item) { context ->
            if (!ready) {
                GuiSounds.denied(context.player)
                return@StaticSlot
            }
            executeBulkOpen(context, crate.id, amount)
        }
    }

    private fun maxTile(crate: CrateDefinition, maxAffordable: Int, cap: Int): StaticSlot {
        val ready = maxAffordable > 0
        val material = if (ready) Material.GOLD_BLOCK else Material.GRAY_STAINED_GLASS_PANE
        val item = ItemStack(material)
        item.amount = maxAffordable.coerceIn(1, 64)
        val placeholders = mapOf(
            "amount" to maxAffordable,
            "cap" to cap,
            "cost" to renderCost(crate, maxAffordable.coerceAtLeast(1)),
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_BULK_MAX_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_BULK_MAX_LORE, placeholders))
            if (ready) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(item) { context ->
            if (!ready) {
                GuiSounds.denied(context.player)
                return@StaticSlot
            }
            executeBulkOpen(context, crate.id, maxAffordable)
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

    private fun executeBulkOpen(context: GuiClickContext, crateId: String, amount: Int) {
        val scope = SlateAPI.pluginScope() ?: return
        val openService = SlateAPI.openService() ?: return
        GuiSounds.success(context.player)
        context.session.closeRequested = true
        scope.launch {
            openService.openById(context.player, crateId, amount)
        }
    }

    private fun renderCost(crate: CrateDefinition, amount: Int): String {
        val economy = SlateAPI.economyService()
        val costs = crate.costs.filterIsInstance<CrateCost.CurrencyCost>()
        if (costs.isEmpty()) return "—"
        return costs.joinToString(", ") { cost ->
            val perCost = cost.amount.multipliedBy(amount.coerceAtLeast(1))
            val rendered = economy?.formatAmount(perCost, cost.provider, cost.currency)
                ?: perCost.asPersistedString()
            "$rendered ${cost.currency}"
        }
    }

    companion object {
        private val DEFAULT_AMOUNTS = listOf(1, 3, 5, 10)
        private val AMOUNT_SLOTS = listOf(10, 12, 13, 14)
    }
}
