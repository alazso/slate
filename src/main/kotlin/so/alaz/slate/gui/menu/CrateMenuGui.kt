package so.alaz.slate.gui.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateState
import so.alaz.slate.crate.CrateVisibility
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
import so.alaz.slate.opening.FailureReason
import so.alaz.slate.opening.OpenPreflightResult
import so.alaz.slate.player.PendingSummary
import so.alaz.slate.player.PityProgressEntry

/**
 * Main player hub. Always reachable via `/crates menu`.
 *
 * Implements GUI-REFACTOR.md §1:
 *   - Info head (player skull) at slot 4 with key/lifetime/pending counts
 *   - 7×3 paginated crate grid at slots 10..16, 19..25, 28..34
 *   - Pagination arrows at slot 39/41 with a page indicator at slot 40
 *   - KEYS at 46, HISTORY at 47, CLAIMS at 49, PITY at 51
 */
class CrateMenuGui(
    private val player: Player,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_MENU_TITLE),
    rows = 6,
) {
    private var page: Int = 0
    private var filteredCrates: List<CrateDefinition> = emptyList()
    private var totalPages: Int = 1

    override suspend fun onBeforeOpen(player: Player) {
        val border = ItemStack(Material.GRAY_STAINED_GLASS_PANE).withBlankName()
        fillBorder(border)

        val crateManager = SlateAPI.crateManager() ?: return
        val eligibility = SlateAPI.crateEligibilityService() ?: return
        val queryService = SlateAPI.playerCrateQueryService() ?: return
        val keyService = SlateAPI.keyService()
        val openService = SlateAPI.openService()

        filteredCrates = crateManager.all()
            .filter { eligibility.visibilityFor(player, it) == CrateVisibility.VISIBLE }
            .sortedBy { it.id }
        totalPages = ((filteredCrates.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0

        val keyWallet = queryService.keyWallet(player)
        val pending = queryService.pendingSummary(player)
        val lifetime = queryService.lifetimeOpens(player)
        val closestPity = queryService.closestPityProgress(player)

        slot(4, infoHead(player, keyWallet.totalVirtualKeys, lifetime, pending))

        val pageStart = page * GRID_SLOTS.size
        val pageCrates = filteredCrates.drop(pageStart).take(GRID_SLOTS.size)
        for ((index, crate) in pageCrates.withIndex()) {
            val keys = keyService?.getVirtualKeys(player.uniqueId, crate.id) ?: 0
            val preflight = openService?.preflightById(player, crate.id)
            slot(GRID_SLOTS[index], crateTile(crate, keys, preflight))
        }
        for (index in pageCrates.size until GRID_SLOTS.size) {
            slot(GRID_SLOTS[index], StaticSlot(border))
        }

        slot(39, prevArrow())
        slot(40, pageIndicator(filteredCrates.size))
        slot(41, nextArrow())

        slot(46, keysButton(keyWallet.totalVirtualKeys))
        slot(47, historyButton(lifetime))
        slot(49, claimsButton(pending))
        slot(51, pityButton(closestPity))
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        val scheduler = SlateAPI.scheduler() ?: return
        refresh(context.player, scheduler, session.inventory)
    }

    private fun fillBorder(border: ItemStack) {
        val sz = rows * 9
        for (i in 0 until 9) slot(i, StaticSlot(border))
        for (i in (sz - 9) until sz) slot(i, StaticSlot(border))
        for (y in 1 until rows - 1) {
            slot(y * 9, StaticSlot(border))
            slot((y * 9) + 8, StaticSlot(border))
        }
    }

    private fun infoHead(player: Player, totalKeys: Int, lifetime: Int, pending: PendingSummary): StaticSlot {
        val item = ItemStack(Material.PLAYER_HEAD)
        val placeholders = mapOf(
            "player" to player.name,
            "totalKeys" to totalKeys,
            "lifetimeOpens" to lifetime,
            "pendingCount" to pending.count,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(player, MessageKeys.GUI_MENU_HEAD_NAME, placeholders))
            meta.lore(GuiText.lore(player, MessageKeys.GUI_MENU_HEAD_LORE, placeholders))
            if (meta is org.bukkit.inventory.meta.SkullMeta) {
                meta.owningPlayer = player
            }
        }
        return StaticSlot(item)
    }

    private fun crateTile(
        crate: CrateDefinition,
        keys: Int,
        preflight: OpenPreflightResult?,
    ): StaticSlot {
        val baseItem = ItemFactory.visualStackForCrateKey(crate, crate.keyDefinition.material)
        val status = statusFor(crate, preflight)
        val ready = preflight is OpenPreflightResult.Ready
        val blockedReason = (preflight as? OpenPreflightResult.Blocked)?.reason
        val placeholders = mapOf(
            "name" to crate.name,
            "id" to crate.id,
            "status" to status,
            "availableKeys" to keys,
            "requiredKeys" to crate.requiredKeys,
            "costSummary" to costSummary(crate),
            "blockedReason" to (blockedReason?.let(::failureLabel) ?: ""),
        )
        val loreKey = when {
            ready -> MessageKeys.GUI_MENU_TILE_LORE_READY
            blockedReason == FailureReason.NO_KEY || blockedReason == FailureReason.INSUFFICIENT_KEYS ->
                MessageKeys.GUI_MENU_TILE_LORE_INSUFFICIENT
            else -> MessageKeys.GUI_MENU_TILE_LORE_BLOCKED
        }
        baseItem.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_MENU_TILE_NAME, placeholders))
            meta.lore(GuiText.lore(loreKey, placeholders))
            if (ready) meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
        }
        if (!ready && (blockedReason == FailureReason.CRATE_DISABLED || blockedReason == FailureReason.CRATE_ARCHIVED || blockedReason == FailureReason.CRATE_OUT_OF_SEASON)) {
            baseItem.type = Material.GRAY_DYE
        }
        return StaticSlot(baseItem) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, CrateDetailGui(context.player, crate.id) { CrateMenuGui(it) })
        }
    }

    private fun statusFor(crate: CrateDefinition, preflight: OpenPreflightResult?): String {
        val cooldownRemaining = (preflight as? OpenPreflightResult.Blocked)
            ?.takeIf { it.reason == FailureReason.ON_COOLDOWN }
            ?.cooldownRemainingMillis ?: 0L
        if (cooldownRemaining > 0L) {
            return GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_COOLDOWN, mapOf("remaining" to formatDuration(cooldownRemaining)))
        }
        return when (crate.lifecycle.state) {
            CrateState.ARCHIVED -> GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_ARCHIVED)
            CrateState.DISABLED -> GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_DISABLED)
            CrateState.HIDDEN -> GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_DISABLED)
            CrateState.SEASONAL ->
                if (crate.lifecycle.isOpenInSeason(java.time.Instant.now())) GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_SEASONAL)
                else GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_OUT_OF_SEASON)
            else -> GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_ACTIVE)
        }
    }

    private fun costSummary(crate: CrateDefinition): String {
        val economy = SlateAPI.economyService()
        val costs = crate.costs.filterIsInstance<so.alaz.slate.crate.CrateCost.CurrencyCost>()
        if (costs.isEmpty()) return "—"
        return costs.joinToString(", ") { cost ->
            val rendered = economy?.formatAmount(cost.amount, cost.provider, cost.currency)
                ?: cost.amount.asPersistedString()
            "$rendered ${cost.currency}"
        }
    }

    private fun prevArrow(): StaticSlot {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PREV_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PREV_LORE))
        }
        if (page <= 0) item.type = Material.GRAY_STAINED_GLASS_PANE
        return StaticSlot(item) { context ->
            if (page > 0) {
                page--
                GuiSounds.pageTurn(context.player)
            } else {
                GuiSounds.denied(context.player)
            }
        }
    }

    private fun nextArrow(): StaticSlot {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_NEXT_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_NEXT_LORE))
        }
        if (page >= totalPages - 1) item.type = Material.GRAY_STAINED_GLASS_PANE
        return StaticSlot(item) { context ->
            if (page < totalPages - 1) {
                page++
                GuiSounds.pageTurn(context.player)
            } else {
                GuiSounds.denied(context.player)
            }
        }
    }

    private fun pageIndicator(total: Int): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf("page" to (page + 1), "total" to totalPages, "count" to total)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_PAGE_INDICATOR_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun keysButton(totalKeys: Int): StaticSlot {
        val item = ItemStack(Material.TRIPWIRE_HOOK)
        val placeholders = mapOf("totalKeys" to totalKeys)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_MENU_KEYS_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_MENU_KEYS_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, KeyWalletGui(context.player) { CrateMenuGui(it) })
        }
    }

    private fun historyButton(lifetime: Int): StaticSlot {
        val item = ItemStack(Material.BOOK)
        val placeholders = mapOf("lifetimeOpens" to lifetime)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_MENU_HISTORY_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_MENU_HISTORY_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, OpenHistoryGui(context.player) { CrateMenuGui(it) })
        }
    }

    private fun claimsButton(pending: PendingSummary): StaticSlot {
        val item = if (pending.count > 0) ItemStack(Material.CHEST) else ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val placeholders = mapOf("pendingCount" to pending.count)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_MENU_CLAIMS_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_MENU_CLAIMS_LORE, placeholders))
            if (pending.count > 0) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(item) { context ->
            if (pending.count <= 0) {
                GuiSounds.denied(context.player)
                return@StaticSlot
            }
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, PendingClaimsGui(context.player) { CrateMenuGui(it) })
        }
    }

    private fun pityButton(closest: PityProgressEntry?): StaticSlot {
        val item = ItemStack(Material.EXPERIENCE_BOTTLE)
        val summary = closest?.let { "${it.crate.name} ${it.current}/${it.required}" }
            ?: GuiText.raw(MessageKeys.GUI_COMMON_NO_DATA)
        val placeholders = mapOf("closestSummary" to summary)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_MENU_PITY_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_MENU_PITY_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, PityProgressGui(context.player) { CrateMenuGui(it) })
        }
    }

    private fun failureLabel(reason: FailureReason): String =
        so.alaz.slate.command.sub.CommandMessages.openFailureReason(reason)

    private fun ItemStack.withBlankName(): ItemStack = apply {
        editMeta { meta -> meta.displayName(GuiText.component(MessageKeys.GUI_COMMON_NO_DATA)) }
    }

    companion object {
        /**
         * Grid slots for the inner 7×3 crate area:
         *   row 1: 10..16
         *   row 2: 19..25
         *   row 3: 28..34
         */
        private val GRID_SLOTS = listOf(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
        )

        internal fun formatDuration(millis: Long): String {
            if (millis <= 0L) return "0s"
            val seconds = millis / 1000L
            if (seconds < 60) return "${seconds}s"
            val minutes = seconds / 60L
            val remSeconds = seconds % 60L
            if (minutes < 60) return if (remSeconds == 0L) "${minutes}m" else "${minutes}m${remSeconds}s"
            val hours = minutes / 60L
            val remMinutes = minutes % 60L
            if (hours < 24) return if (remMinutes == 0L) "${hours}h" else "${hours}h${remMinutes}m"
            val days = hours / 24L
            val remHours = hours % 24L
            return if (remHours == 0L) "${days}d" else "${days}d${remHours}h"
        }
    }
}
