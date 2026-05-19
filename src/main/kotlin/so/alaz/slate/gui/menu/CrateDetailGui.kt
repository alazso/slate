package so.alaz.slate.gui.menu

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.PrizeTier
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.gui.preview.PreviewGui
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.item.visualStackForCrateKey
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.OpenPreflightResult
import so.alaz.slate.opening.FailureReason
import so.alaz.slate.player.AnchorLookup
import so.alaz.slate.player.PityProgressEntry

/**
 * Crate Detail menu — opened when the player clicks a crate tile in `CrateMenuGui`.
 *
 * Implements GUI-REFACTOR.md §2:
 *   - Slot 4: crate icon with full info lore
 *   - Slot 10: preview rewards
 *   - Slot 12: dynamic open button (state-aware material/lore)
 *   - Slot 14: locate anchor
 *   - Slot 16: personal stats book (lifetime opens, last opened, pity progress)
 *   - Slots 27..31: per-tier preview tiles
 *   - Slot 40: back arrow
 */
class CrateDetailGui(
    private val owner: Player,
    private val crateId: String,
    private val backFactory: (Player) -> SlateGui,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_CRATE_DETAIL_TITLE, mapOf("name" to (SlateAPI.crateManager()?.byId(crateId)?.name ?: crateId))),
    rows = 5,
) {
    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val crate = SlateAPI.crateManager()?.byId(crateId)
        if (crate == null) {
            slot(22, StaticSlot(unknownIcon()))
            slot(40, backTile())
            return
        }
        val keyService = SlateAPI.keyService()
        val openService = SlateAPI.openService()
        val query = SlateAPI.playerCrateQueryService() ?: return

        val keys = keyService?.getVirtualKeys(player.uniqueId, crate.id) ?: 0
        val preflight = openService?.preflightById(player, crate.id)
        val anchor = query.nearestAnchorForCrate(player, crate.id) ?: query.nearestAnchor(player)
        val lifetime = query.lifetimeOpens(player, crate.id)
        val latest = query.latestOpen(player, crate.id)
        val pity = query.pityProgress(player).firstOrNull { it.crate.id == crate.id }

        slot(4, StaticSlot(crateIcon(player, crate, keys, pity, preflight)))
        slot(10, previewTile(crate))
        slot(12, openTile(crate, keys, preflight))
        slot(14, locateTile(crate, anchor))
        slot(16, statsTile(crate, keys, lifetime, latest?.openedAt, pity))

        for ((index, tier) in crate.tiers.values.take(TIER_SLOTS.size).withIndex()) {
            slot(TIER_SLOTS[index], tierTile(crate, tier))
        }
        slot(40, backTile())
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

    private fun unknownIcon(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId)))
        }
        return item
    }

    private fun crateIcon(
        player: Player,
        crate: CrateDefinition,
        keys: Int,
        pity: PityProgressEntry?,
        preflight: OpenPreflightResult?,
    ): ItemStack {
        val stack = ItemFactory.visualStackForCrateKey(crate, crate.keyDefinition.material)
        val cooldownRemaining = (preflight as? OpenPreflightResult.Blocked)
            ?.takeIf { it.reason == FailureReason.ON_COOLDOWN }
            ?.cooldownRemainingMillis ?: 0L
        val placeholders = mapOf(
            "name" to crate.name,
            "id" to crate.id,
            "requiredKeys" to crate.requiredKeys,
            "availableKeys" to keys,
            "cooldown" to if (cooldownRemaining > 0L) CrateMenuGui.formatDuration(cooldownRemaining) else crate.cooldown.toString(),
            "maxPulls" to (if (crate.maxPulls == -1) "∞" else crate.maxPulls),
            "pityCurrent" to (pity?.current ?: 0),
            "pityRequired" to (pity?.required ?: 0),
            "guaranteedTier" to (pity?.guaranteedTier ?: "—"),
            "worlds" to crate.allowedWorlds.joinToString(", ").ifBlank { "all" },
            "costSummary" to crateCostSummary(crate),
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(player, MessageKeys.GUI_CRATE_DETAIL_TITLE, placeholders))
            meta.lore(GuiText.lore(player, MessageKeys.GUI_CRATE_DETAIL_STATS_LORE, placeholders))
        }
        return stack
    }

    private fun previewTile(crate: CrateDefinition): StaticSlot {
        val item = ItemStack(Material.ENDER_EYE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_PREVIEW_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_PREVIEW_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(
                context.player,
                PreviewGui(crate = crate, backFactory = { player -> backFactory(player) }),
            )
        }
    }

    private fun openTile(crate: CrateDefinition, keys: Int, preflight: OpenPreflightResult?): StaticSlot {
        val ready = preflight is OpenPreflightResult.Ready
        val cooldownMs = (preflight as? OpenPreflightResult.Blocked)
            ?.takeIf { it.reason == FailureReason.ON_COOLDOWN }
            ?.cooldownRemainingMillis ?: 0L
        val material = when {
            ready -> Material.LIME_CONCRETE
            cooldownMs > 0L -> Material.YELLOW_CONCRETE
            preflight is OpenPreflightResult.Blocked &&
                (preflight.reason == FailureReason.NO_KEY || preflight.reason == FailureReason.INSUFFICIENT_KEYS) -> Material.RED_CONCRETE
            else -> Material.GRAY_CONCRETE
        }
        val placeholders = mapOf(
            "availableKeys" to keys,
            "requiredKeys" to crate.requiredKeys,
            "costSummary" to crateCostSummary(crate),
            "reason" to ((preflight as? OpenPreflightResult.Blocked)
                ?.let {
                    if (cooldownMs > 0L) GuiText.raw(MessageKeys.GUI_MENU_TILE_STATUS_COOLDOWN, mapOf("remaining" to CrateMenuGui.formatDuration(cooldownMs)))
                    else CommandMessages.openFailureReason(it.reason)
                } ?: ""),
        )
        val item = ItemStack(material)
        item.editMeta { meta ->
            if (ready) {
                meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_OPEN_READY_NAME, placeholders))
                meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_OPEN_READY_LORE, placeholders))
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
            } else {
                meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_OPEN_BLOCKED_NAME, placeholders))
                meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_OPEN_BLOCKED_LORE, placeholders))
            }
        }
        return StaticSlot(item) { context ->
            handleOpenClick(context, crate, preflight)
        }
    }

    private suspend fun handleOpenClick(
        context: GuiClickContext,
        crate: CrateDefinition,
        preflight: OpenPreflightResult?,
    ) {
        when (context.clickType) {
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                GuiSounds.navigate(context.player)
                SlateAPI.guiService()?.openGui(
                    context.player,
                    BulkOpenConfirmGui(context.player, crate.id) { backFactory(it) },
                )
            }
            else -> {
                if (preflight is OpenPreflightResult.Ready) {
                    GuiSounds.success(context.player)
                    SlateAPI.messageService()?.hotbar(context.player, MessageKeys.GUI_CRATE_DETAIL_OPEN_READY_HOTBAR, mapOf("crate" to crate.name))
                    context.session.closeRequested = true
                    val openService = SlateAPI.openService() ?: return
                    val scope = SlateAPI.pluginScope() ?: return
                    scope.launch {
                        openService.openById(context.player, crate.id, 1)
                    }
                } else {
                    GuiSounds.denied(context.player)
                    SlateAPI.messageService()?.hotbar(context.player, MessageKeys.GUI_COMMON_DENIED_HOTBAR)
                }
            }
        }
    }

    private fun locateTile(crate: CrateDefinition, anchor: AnchorLookup?): StaticSlot {
        val item = ItemStack(Material.COMPASS)
        val placeholders = mapOf(
            "crate" to crate.id,
            "location" to (anchor?.let { "${it.x}, ${it.y}, ${it.z}" }
                ?: GuiText.raw(MessageKeys.GUI_CRATE_DETAIL_LOCATE_NONE)),
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_LOCATE_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_LOCATE_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            if (anchor == null) {
                GuiSounds.denied(context.player)
                CommandMessages.chat(context.player, MessageKeys.CRATES_LOCATE_NONE)
            } else {
                GuiSounds.navigate(context.player)
                CommandMessages.chat(
                    context.player,
                    MessageKeys.CRATES_LOCATE_NEAREST,
                    mapOf("crate" to anchor.crateId, "x" to anchor.x, "y" to anchor.y, "z" to anchor.z),
                )
            }
        }
    }

    private fun statsTile(
        crate: CrateDefinition,
        keys: Int,
        lifetime: Int,
        lastOpenedAt: Instant?,
        pity: PityProgressEntry?,
    ): StaticSlot {
        val item = ItemStack(Material.KNOWLEDGE_BOOK)
        val pityProgress = pity?.let { progressBar(it.current, it.required) } ?: "—"
        val lastOpened = lastOpenedAt?.let { relativeTime(it) } ?: GuiText.raw(MessageKeys.GUI_COMMON_NO_DATA)
        val placeholders = mapOf(
            "lifetimeOpens" to lifetime,
            "lastOpened" to lastOpened,
            "pityProgress" to pityProgress,
            "availableKeys" to keys,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_STATS_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_STATS_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun tierTile(crate: CrateDefinition, tier: PrizeTier): StaticSlot {
        val rewardCount = crate.prizes.count { it.tier == tier.id }
        val totalWeight = crate.tiers.values.sumOf { it.weight }.coerceAtLeast(1)
        val chance = ((tier.weight.toDouble() / totalWeight) * 100.0).coerceAtLeast(0.0)
        val color = tierColor(tier.id)
        val material = tierMaterial(tier.id)
        val item = ItemStack(material)
        val placeholders = mapOf(
            "tierName" to tier.displayName,
            "tierColor" to color,
            "tierChance" to String.format(java.util.Locale.ROOT, "%.2f%%", chance),
            "rewardCount" to rewardCount,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_DETAIL_TIER_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_DETAIL_TIER_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(
                context.player,
                PreviewGui(crate, backFactory = { backFactory(it) }, initialTierFilter = tier.id),
            )
        }
    }

    private fun backTile(): StaticSlot {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_BACK_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_BACK_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            SlateAPI.guiService()?.openGui(context.player, backFactory(context.player))
        }
    }

    private fun crateCostSummary(crate: CrateDefinition): String {
        val economy = SlateAPI.economyService()
        val costs = crate.costs.filterIsInstance<so.alaz.slate.crate.CrateCost.CurrencyCost>()
        if (costs.isEmpty()) return "—"
        return costs.joinToString(", ") { cost ->
            val rendered = economy?.formatAmount(cost.amount, cost.provider, cost.currency)
                ?: cost.amount.asPersistedString()
            "$rendered ${cost.currency}"
        }
    }

    private fun progressBar(current: Int, required: Int): String {
        if (required <= 0) return "—"
        val ratio = (current.toDouble() / required).coerceIn(0.0, 1.0)
        val filled = (ratio * BAR_SEGMENTS).toInt().coerceIn(0, BAR_SEGMENTS)
        val empty = BAR_SEGMENTS - filled
        return "[${"█".repeat(filled)}${"░".repeat(empty)}] $current/$required"
    }

    private fun relativeTime(instant: Instant): String {
        val now = Instant.now()
        val seconds = Duration.between(instant, now).seconds.coerceAtLeast(0L)
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            seconds < 2_592_000 -> "${seconds / 86_400}d ago"
            else -> "${seconds / 2_592_000}mo ago"
        }
    }

    private fun tierColor(tierId: String): String = TIER_COLORS[tierId.lowercase()] ?: "gold"

    private fun tierMaterial(tierId: String): Material = when (tierId.lowercase()) {
        "legendary", "mythic", "epic" -> Material.GOLD_BLOCK
        "rare" -> Material.LAPIS_BLOCK
        "uncommon" -> Material.EMERALD_BLOCK
        "common" -> Material.IRON_BLOCK
        else -> Material.AMETHYST_BLOCK
    }

    companion object {
        private val TIER_SLOTS = listOf(27, 28, 29, 30, 31)
        private const val BAR_SEGMENTS = 10
        private val TIER_COLORS = mapOf(
            "legendary" to "gold",
            "mythic" to "light_purple",
            "epic" to "dark_purple",
            "rare" to "aqua",
            "uncommon" to "green",
            "common" to "gray",
        )
    }
}
