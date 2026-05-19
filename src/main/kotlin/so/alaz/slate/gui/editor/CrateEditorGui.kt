package so.alaz.slate.gui.editor

import kotlin.time.Duration.Companion.milliseconds
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateAnimationConfig
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.crate.CrateDefinition
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

/**
 * Crate Editor — GUI-REFACTOR.md §10.
 *
 * 6-row layout:
 *   - Row 0 (slot 4): crate header
 *   - Row 1 (slots 9..17): tab bar (active tab gets glint)
 *   - Row 2 (slots 18..26): separator
 *   - Rows 3..4 (slots 27..44): tab content (varies per tab)
 *   - Row 5 (slots 45..53): Simulate / Chances / Save / Discard / Back
 *
 * Mutations write through to the YAML and reload the crate via `CrateManager`.
 */
class CrateEditorGui(
    crate: CrateDefinition,
    private var activeTab: Tab = Tab.GENERAL,
    private var costsPage: Int = 0,
    private var rewardsPage: Int = 0,
    private var tiersPage: Int = 0,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_EDITOR_TITLE, mapOf("name" to crate.name)),
    rows = 6,
) {
    private val crateId: String = crate.id

    override suspend fun onBeforeOpen(player: Player) {
        val crate = SlateAPI.crateManager()?.byId(crateId) ?: return renderMissing()
        val border = StaticSlot(blank())
        for (i in 18..26) slot(i, border)

        slot(4, headerTile(crate))
        renderTabBar(crate)
        renderActiveTab(crate)
        renderBottomBar(crate)
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        rebuild(context.player)
    }

    private fun renderMissing() {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to crateId)))
        }
        slot(22, StaticSlot(item))
    }

    private fun headerTile(crate: CrateDefinition): StaticSlot {
        val item = ItemStack(crate.keyDefinition.material)
        val placeholders = headerPlaceholders(crate)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_HEADER_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_HEADER_LORE, placeholders))
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)
        }
        return StaticSlot(item)
    }

    private fun headerPlaceholders(crate: CrateDefinition) = mapOf(
        "name" to crate.name,
        "id" to crate.id,
        "state" to crate.lifecycle.state.name,
        "tiers" to crate.tiers.size,
        "rewards" to crate.prizes.size,
        "costs" to crate.costs.size,
    )

    private fun renderTabBar(crate: CrateDefinition) {
        Tab.values().forEachIndexed { index, tab ->
            slot(9 + index, tabTile(tab))
        }
    }

    private fun tabTile(tab: Tab): StaticSlot {
        val item = ItemStack(tab.material)
        val active = tab == activeTab
        val placeholders = mapOf("tab" to tab.label)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_TAB_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_TAB_LORE, placeholders))
            if (active) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
        }
        return StaticSlot(item) { context ->
            if (activeTab != tab) {
                activeTab = tab
                GuiSounds.click(context.player)
            }
        }
    }

    private fun renderActiveTab(crate: CrateDefinition) {
        for (i in 27..44) slot(i, null)
        when (activeTab) {
            Tab.GENERAL -> renderGeneralTab(crate)
            Tab.KEYS -> renderKeysTab(crate)
            Tab.TIERS -> renderTiersTab(crate)
            Tab.REWARDS -> renderRewardsTab(crate)
            Tab.COSTS -> renderCostsTab(crate)
            Tab.DISPLAY -> renderDisplayTab(crate)
            Tab.ANIMATION -> renderAnimationTab(crate)
            Tab.ELIGIBILITY -> renderEligibilityTab(crate)
            Tab.PREVIEW -> renderPreviewTab(crate)
        }
    }

    private fun renderGeneralTab(crate: CrateDefinition) {
        slot(27, fieldTile(Material.PAPER, "Name", crate.name) { ctx -> GeneralMutationSupport.promptName(ctx, crateId) })
        slot(28, fieldTile(stateDye(crate), "Status", crate.lifecycle.state.name) { ctx -> GeneralMutationSupport.cycleState(ctx, crateId) })
        slot(29, fieldTile(Material.TRIPWIRE_HOOK, "Required Keys", crate.requiredKeys.toString()) { ctx -> GeneralMutationSupport.promptRequiredKeys(ctx, crateId) })
        slot(30, fieldTile(Material.CLOCK, "Cooldown", formatDuration(crate.cooldown.inWholeMilliseconds.milliseconds.inWholeMilliseconds)) { ctx -> GeneralMutationSupport.promptCooldown(ctx, crateId) })
        slot(31, fieldTile(Material.ENDER_PEARL, "Max Pulls", if (crate.maxPulls < 0) "∞" else crate.maxPulls.toString()) { ctx -> GeneralMutationSupport.promptMaxPulls(ctx, crateId) })
        slot(32, fieldTile(Material.GRASS_BLOCK, "Allowed Worlds", crate.allowedWorlds.joinToString(", ").ifBlank { "all" }) { ctx -> GeneralMutationSupport.promptAllowedWorlds(ctx, crateId) })
    }

    private fun renderKeysTab(crate: CrateDefinition) {
        slot(27, summaryTile(crate.keyDefinition.material, "Material", crate.keyDefinition.material.name))
        slot(28, summaryTile(Material.NAME_TAG, "Name", crate.keyDefinition.name))
        slot(29, summaryTile(Material.PAPER, "Provider", crate.keyDefinition.provider.name))
        slot(30, summaryTile(Material.ENCHANTED_BOOK, "Glow", crate.keyDefinition.glowing.toString()))
        slot(31, openDedicatedTile("Open Key Editor", Material.WRITABLE_BOOK) { ctx ->
            EditorNavigationSupport.openGui(ctx, KeyEditorGui(crate))
        })
    }

    private fun renderTiersTab(crate: CrateDefinition) {
        val tiers = crate.tiers.values.toList()
        if (tiers.isEmpty()) {
            slot(35, emptyTile("No tiers configured"))
        } else {
            val pageSize = 14
            val totalPages = ((tiers.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            if (tiersPage >= totalPages) tiersPage = totalPages - 1
            val items = tiers.drop(tiersPage * pageSize).take(pageSize)
            val targetSlots = listOf(27, 28, 29, 30, 31, 32, 33, 36, 37, 38, 39, 40, 41, 42)
            for ((i, tier) in items.withIndex()) {
                slot(targetSlots[i], summaryTile(Material.EXPERIENCE_BOTTLE, tier.id, "weight ${tier.weight}"))
            }
        }
        slot(43, openDedicatedTile("Open Tier Editor", Material.WRITABLE_BOOK) { ctx ->
            EditorNavigationSupport.openGui(ctx, TierEditorGui(crate))
        })
    }

    private fun renderRewardsTab(crate: CrateDefinition) {
        val prizes = crate.prizes
        if (prizes.isEmpty()) {
            slot(35, emptyTile("No rewards configured"))
        } else {
            val pageSize = 14
            val totalPages = ((prizes.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            if (rewardsPage >= totalPages) rewardsPage = totalPages - 1
            val items = prizes.drop(rewardsPage * pageSize).take(pageSize)
            val targetSlots = listOf(27, 28, 29, 30, 31, 32, 33, 36, 37, 38, 39, 40, 41, 42)
            for ((i, prize) in items.withIndex()) {
                slot(targetSlots[i], rewardSummaryTile(crate, prize))
            }
        }
        slot(43, openDedicatedTile("New Reward", Material.LIME_DYE) { ctx ->
            EditorNavigationSupport.openGui(ctx, RewardEditorGui(crate))
        })
    }

    private fun renderCostsTab(crate: CrateDefinition) {
        val costs = CostMutationSupport.currencyCosts(crate.costs)
        if (costs.isEmpty()) {
            slot(35, emptyTile("No costs configured"))
        } else {
            val pageSize = 14
            val totalPages = ((costs.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            if (costsPage >= totalPages) costsPage = totalPages - 1
            val items = costs.drop(costsPage * pageSize).take(pageSize)
            val targetSlots = listOf(27, 28, 29, 30, 31, 32, 33, 36, 37, 38, 39, 40, 41, 42)
            for ((i, cost) in items.withIndex()) {
                val index = costsPage * pageSize + i
                slot(targetSlots[i], costTile(cost, index))
            }
        }
        slot(43, fieldTile(Material.LIME_DYE, "Add Cost", "provider:currency:amount") { ctx ->
            CostMutationSupport.promptAddCurrency(ctx, crateId)
        })
    }

    private fun renderDisplayTab(crate: CrateDefinition) {
        slot(27, summaryTile(Material.ITEM_FRAME, "Enabled", crate.display.enabled.toString()))
        slot(28, summaryTile(crate.display.item.material, "Material", crate.display.item.material.name))
        slot(29, summaryTile(Material.PAPER, "Billboard", crate.display.billboard.name))
        slot(30, summaryTile(Material.PAPER, "View Range", crate.display.viewRange.toString()))
        slot(31, summaryTile(Material.PAPER, "Lines", crate.display.lines.size.toString()))
        slot(32, openDedicatedTile("Open Display Editor", Material.WRITABLE_BOOK) { ctx ->
            EditorNavigationSupport.openGui(ctx, DisplayEditorGui(crate))
        })
    }

    private fun renderAnimationTab(crate: CrateDefinition) {
        val animation: CrateAnimationConfig = crate.animation ?: CrateAnimationConfig()
        slot(27, fieldTile(if (animation.enabled) Material.LIME_DYE else Material.GRAY_DYE, "Enabled", animation.enabled.toString()) { ctx ->
            AnimationMutationSupport.toggleEnabled(ctx, crateId)
        })
        slot(28, fieldTile(Material.JUKEBOX, "Template", animation.template) { ctx ->
            AnimationMutationSupport.cycleTemplate(ctx, crateId)
        })
        slot(29, fieldTile(Material.CLOCK, "Duration (ticks)", animation.durationTicks.toString()) { ctx ->
            AnimationMutationSupport.promptDurationTicks(ctx, crateId)
        })
        slot(30, fieldTile(Material.REPEATER, "Tick Interval", animation.tickInterval.toString()) { ctx ->
            AnimationMutationSupport.promptTickInterval(ctx, crateId)
        })
        slot(31, fieldTile(Material.NOTE_BLOCK, "Spin Speed", animation.spinSpeedMultiplier.toString()) { ctx ->
            AnimationMutationSupport.promptSpinSpeed(ctx, crateId)
        })
        slot(32, fieldTile(Material.BEACON, "Decoys", animation.decoyCount.toString()) { ctx ->
            AnimationMutationSupport.promptDecoyCount(ctx, crateId)
        })
        slot(33, fieldTile(Material.MUSIC_DISC_CAT, "Sound Tick", animation.soundTick ?: "none") { ctx ->
            AnimationMutationSupport.promptSound(ctx, crateId, AnimationMutationSupport.SoundField.TICK)
        })
        slot(36, fieldTile(Material.MUSIC_DISC_FAR, "Sound Reveal", animation.soundReveal ?: "none") { ctx ->
            AnimationMutationSupport.promptSound(ctx, crateId, AnimationMutationSupport.SoundField.REVEAL)
        })
    }

    private fun renderEligibilityTab(crate: CrateDefinition) {
        val perms = crate.requirements.permissions
        val regions = crate.requirements.regions
        slot(27, fieldTile(Material.SHIELD, "Open Perms", perms.open.joinToString().ifBlank { "none" }) { ctx ->
            EligibilityMutationSupport.promptSetPermissions(ctx, crateId, EligibilityMutationSupport.PermissionScope.OPEN)
        })
        slot(28, fieldTile(Material.SHIELD, "Preview Perms", perms.preview.joinToString().ifBlank { "none" }) { ctx ->
            EligibilityMutationSupport.promptSetPermissions(ctx, crateId, EligibilityMutationSupport.PermissionScope.PREVIEW)
        })
        slot(29, fieldTile(Material.SHIELD, "List Perms", perms.list.joinToString().ifBlank { "none" }) { ctx ->
            EligibilityMutationSupport.promptSetPermissions(ctx, crateId, EligibilityMutationSupport.PermissionScope.LIST)
        })
        slot(30, fieldTile(Material.GRASS_BLOCK, "Allowed Regions", regions.allow.joinToString().ifBlank { "none" }) { ctx ->
            EligibilityMutationSupport.promptSetRegions(ctx, crateId, EligibilityMutationSupport.RegionScope.ALLOW)
        })
        slot(31, fieldTile(Material.GRASS_BLOCK, "Denied Regions", regions.deny.joinToString().ifBlank { "none" }) { ctx ->
            EligibilityMutationSupport.promptSetRegions(ctx, crateId, EligibilityMutationSupport.RegionScope.DENY)
        })
        slot(32, fieldTile(if (regions.requireWorldGuard) Material.LIME_DYE else Material.GRAY_DYE, "Require WorldGuard", regions.requireWorldGuard.toString()) { ctx ->
            EligibilityMutationSupport.toggleRequireWorldGuard(ctx, crateId)
        })
    }

    private fun renderPreviewTab(crate: CrateDefinition) {
        val preview = crate.preview
        slot(27, summaryTile(Material.SPYGLASS, "Enabled", (preview?.enabled ?: true).toString()))
        slot(28, summaryTile(Material.PAPER, "Rows", (preview?.rows ?: 6).toString()))
        slot(29, summaryTile(Material.PAPER, "Slots", (preview?.contentSlots?.size ?: 21).toString()))
        slot(30, summaryTile(Material.PAPER, "Show Chances", (preview?.showChances ?: true).toString()))
        slot(31, summaryTile(Material.PAPER, "Show Tiers", (preview?.showTiers ?: true).toString()))
    }

    private fun renderBottomBar(crate: CrateDefinition) {
        slot(45, fieldTile(Material.HOPPER, "Simulate", "Click to roll N times") { ctx ->
            val raw = EditorPromptSupport.prompt(ctx, "How many rolls to simulate?", SlateAPI.messageService()) ?: return@fieldTile false
            val rolls = raw.trim().toIntOrNull()?.coerceIn(1, 100_000)
            if (rolls == null) {
                GuiSounds.denied(ctx.player)
                CommandMessages.raw(ctx.player, "<red>Invalid roll count.</red>", withPrefix = true)
                return@fieldTile false
            }
            ctx.player.performCommand("sla crate simulate ${crate.id} $rolls")
            GuiSounds.success(ctx.player)
            true
        })
        slot(46, fieldTile(Material.COMPARATOR, "Chances", "Show probability table") { ctx ->
            ctx.player.performCommand("sla crate probabilities ${crate.id}")
            GuiSounds.success(ctx.player)
            true
        })
        slot(48, fieldTile(Material.LIME_WOOL, "Save & Reload", "Persist & reload now") { ctx ->
            val editable = EditorCrateAccess.loadForEdit(crateId)
            if (editable == null) {
                GuiSounds.denied(ctx.player)
                CommandMessages.raw(ctx.player, "<red>Crate file not found.</red>", withPrefix = true)
                return@fieldTile false
            }
            val result = EditorCrateAccess.saveAndReload(editable.file, editable.yaml)
            if (result.success) {
                GuiSounds.success(ctx.player)
                SlateAPI.messageService()?.hotbar(ctx.player, MessageKeys.GUI_EDITOR_SAVE_OK, mapOf("crate" to crate.id))
            } else {
                GuiSounds.denied(ctx.player)
                CommandMessages.raw(ctx.player, "<red>${result.message ?: "Save failed."}</red>", withPrefix = true)
            }
            true
        })
        slot(50, fieldTile(Material.RED_WOOL, "Discard", "Reload from disk, drop unsaved edits") { ctx ->
            val summary = SlateAPI.crateManager()?.reload()
            if (summary == null) {
                GuiSounds.denied(ctx.player)
                return@fieldTile false
            }
            GuiSounds.success(ctx.player)
            SlateAPI.messageService()?.hotbar(ctx.player, MessageKeys.GUI_EDITOR_DISCARD_OK, mapOf("crate" to crate.id))
            true
        })
        slot(53, fieldTile(Material.ARROW, "Back", "Return to Crate Manager") { ctx ->
            EditorNavigationSupport.openGui(ctx, CrateManagerGui())
            true
        })
    }

    private fun fieldTile(material: Material, label: String, value: String, action: suspend (GuiClickContext) -> Boolean): StaticSlot {
        val item = ItemStack(material)
        val placeholders = mapOf("label" to label, "value" to value)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_FIELD_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_FIELD_LORE, placeholders))
        }
        return StaticSlot(item) { ctx -> action(ctx) }
    }

    private fun summaryTile(material: Material, label: String, value: String): StaticSlot {
        val item = ItemStack(material)
        val placeholders = mapOf("label" to label, "value" to value)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_FIELD_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_SUMMARY_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun openDedicatedTile(label: String, material: Material, action: suspend (GuiClickContext) -> Unit): StaticSlot {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_OPEN_DEDICATED_NAME, mapOf("label" to label)))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_OPEN_DEDICATED_LORE))
        }
        return StaticSlot(item) { ctx ->
            GuiSounds.navigate(ctx.player)
            action(ctx)
        }
    }

    private fun emptyTile(text: String): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_EMPTY_NAME, mapOf("text" to text)))
        }
        return StaticSlot(item)
    }

    private fun costTile(cost: CrateCost.CurrencyCost, index: Int): StaticSlot {
        val item = ItemStack(Material.GOLD_INGOT)
        val placeholders = mapOf(
            "provider" to cost.provider,
            "currency" to cost.currency,
            "amount" to cost.amount.asPersistedString(),
            "index" to index,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_COST_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_COST_LORE, placeholders))
        }
        return StaticSlot(item) { ctx ->
            when (ctx.clickType) {
                ClickType.SHIFT_RIGHT -> CostMutationSupport.removeCost(ctx, crateId, index)
                else -> CostMutationSupport.promptEditAmount(ctx, crateId, index)
            }
        }
    }

    private fun rewardSummaryTile(crate: CrateDefinition, prize: so.alaz.slate.crate.Prize): StaticSlot {
        val firstItem = prize.items.firstOrNull()
        val stack = firstItem?.let { ItemFactory.visualStackForPrizeItem(it, Material.PAPER) }
            ?: ItemStack(Material.PAPER)
        val placeholders = mapOf("name" to prize.name, "tier" to prize.tier, "weight" to prize.weight)
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_REWARD_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_REWARD_LORE, placeholders))
        }
        return StaticSlot(stack) { ctx ->
            GuiSounds.navigate(ctx.player)
            EditorNavigationSupport.openGui(ctx, RewardEditorGui(crate, prize.id))
        }
    }

    private fun stateDye(crate: CrateDefinition): Material = when (crate.lifecycle.state) {
        so.alaz.slate.crate.CrateState.ACTIVE -> Material.LIME_DYE
        so.alaz.slate.crate.CrateState.DISABLED -> Material.GRAY_DYE
        so.alaz.slate.crate.CrateState.HIDDEN -> Material.CYAN_DYE
        so.alaz.slate.crate.CrateState.SEASONAL -> Material.YELLOW_DYE
        so.alaz.slate.crate.CrateState.ARCHIVED -> Material.RED_DYE
    }

    private fun blank(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.empty()) }
        return item
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "off"
        val seconds = ms / 1000
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0) append("${secs}s")
        }.trim().ifBlank { "${ms}ms" }
    }

    enum class Tab(val label: String, val material: Material) {
        GENERAL("General", Material.BOOK),
        KEYS("Keys", Material.TRIPWIRE_HOOK),
        TIERS("Tiers", Material.EXPERIENCE_BOTTLE),
        REWARDS("Rewards", Material.EMERALD),
        COSTS("Costs", Material.GOLD_INGOT),
        DISPLAY("Display", Material.ITEM_FRAME),
        ANIMATION("Animation", Material.JUKEBOX),
        ELIGIBILITY("Eligibility", Material.SHIELD),
        PREVIEW("Preview", Material.SPYGLASS),
    }
}
