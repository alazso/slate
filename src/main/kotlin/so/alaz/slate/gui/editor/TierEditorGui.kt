package so.alaz.slate.gui.editor

import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.command.sub.TierMutationSupport
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageService
import so.alaz.slate.probability.ChanceFormatter
import so.alaz.slate.probability.ProbabilityEngine
import so.alaz.slate.probability.SelectionStructure
import so.alaz.slate.util.ComponentUtil

class TierEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Tiers: ${crate.id}", 6) {
    private val draft = TierEditorDraft.fromCrate(crate)

    override suspend fun onBeforeOpen(player: Player) {
        if (!isTieredCrate()) {
            renderNotTieredState()
            return
        }

        val tierIds = draft.state.tiers.keys.sorted()
        tierIds.take(27).forEachIndexed { index, tierId ->
            val tier = draft.state.tiers.getValue(tierId)
            val selected = tierId == draft.selectedTierId
            val stack = ItemStack(if (selected) Material.BOOK else Material.PAPER)
            val rewardsUsingTier = TierMutationSupport.rewardsUsingTier(draft.state, tierId)
            stack.editMeta { meta ->
                val titlePrefix = if (selected) "<green>[Selected]</green> " else ""
                meta.displayName(ComponentUtil.parse("$titlePrefix<yellow>$tierId</yellow>"))
                meta.lore(
                    listOf(
                        "<gray>Name: <white>${tier.displayName}</white></gray>",
                        "<gray>Weight: <white>${tier.weight}</white></gray>",
                        "<gray>Rewards: <white>${rewardsUsingTier.size}</white></gray>",
                        "<dark_gray>Click to select</dark_gray>",
                    ).map(ComponentUtil::parse),
                )
            }
            slot(index, StaticSlot(stack) { draft.selectedTierId = tierId })
        }

        if (tierIds.isEmpty()) {
            val empty = ItemStack(Material.BOOK)
            empty.editMeta { meta ->
                meta.displayName(ComponentUtil.parse("<gray>No tiers configured</gray>"))
                meta.lore(listOf(ComponentUtil.parse("<dark_gray>Use Create Tier to add one.</dark_gray>")))
            }
            slot(13, StaticSlot(empty))
        }

        slot(
            27,
            EditorTileFactory.actionTile(
                material = Material.LIME_DYE,
                title = "<green>Create Tier</green>",
                subtitle = "<gray>Add a new tier id</gray>",
            ) { context ->
                val rawId = prompt(context, "Enter a new tier id (letters, numbers, _, -).")
                val tierId = rawId?.let(TierEditorInputParser::parseTierId)
                if (rawId != null && tierId == null) {
                    messageService()?.chatRaw(context.player, "<red>Invalid tier id format.</red>", withPrefix = true)
                    return@actionTile
                }
                if (tierId == null) return@actionTile

                val result = draft.createTier(tierId)
                handleMutationResult(context, result, fallbackTierId = tierId) {
                    messageService()?.chatRaw(context.player, "<green>Added tier '$tierId'.</green>", withPrefix = true)
                }
            },
        )
        slot(
            28,
            EditorTileFactory.actionTile(
                material = Material.NAME_TAG,
                title = "<yellow>Rename Tier Id</yellow>",
                subtitle = "<gray>${draft.selectedTierId ?: "Select a tier first"}</gray>",
            ) { context ->
                val selectedTierId = requireSelectedTier(context) ?: return@actionTile
                val rawId = prompt(context, "Enter new tier id for '$selectedTierId'.")
                val newTierId = rawId?.let(TierEditorInputParser::parseTierId)
                if (rawId != null && newTierId == null) {
                    messageService()?.chatRaw(context.player, "<red>Invalid tier id format.</red>", withPrefix = true)
                    return@actionTile
                }
                if (newTierId == null) return@actionTile

                val result = draft.renameSelectedTier(newTierId)
                handleMutationResult(context, result, fallbackTierId = selectedTierId) {
                    messageService()?.chatRaw(
                        context.player,
                        "<green>Renamed tier '$selectedTierId' to '$newTierId'.</green>",
                        withPrefix = true,
                    )
                }
            },
        )
        slot(
            29,
            EditorTileFactory.actionTile(
                material = Material.WRITABLE_BOOK,
                title = "<yellow>Edit Display Name</yellow>",
                subtitle = "<gray>${draft.selectedTier()?.displayName ?: "Select a tier first"}</gray>",
            ) { context ->
                val tierId = requireSelectedTier(context) ?: return@actionTile
                val value = prompt(context, "Enter new display name for '$tierId'.")
                if (value == null) return@actionTile

                val result = draft.setSelectedTierName(TierEditorInputParser.parseDisplayName(value))
                handleMutationResult(context, result, fallbackTierId = tierId) {
                    messageService()?.chatRaw(context.player, "<green>Updated display name for '$tierId'.</green>", withPrefix = true)
                }
            },
        )
        slot(
            30,
            EditorTileFactory.actionTile(
                material = Material.REPEATER,
                title = "<yellow>Edit Weight</yellow>",
                subtitle = "<gray>${draft.selectedTier()?.weight ?: "Select a tier first"}</gray>",
            ) { context ->
                val tierId = requireSelectedTier(context) ?: return@actionTile
                val value = prompt(context, "Enter positive integer weight for '$tierId'.")
                val weight = value?.let(TierEditorInputParser::parsePositiveWeight)
                if (value != null && weight == null) {
                    messageService()?.chatRaw(context.player, "<red>Invalid weight. Use a positive integer.</red>", withPrefix = true)
                    return@actionTile
                }
                if (weight == null) return@actionTile

                val result = draft.setSelectedTierWeight(weight)
                handleMutationResult(context, result, fallbackTierId = tierId) {
                    messageService()?.chatRaw(
                        context.player,
                        "<green>Set tier '$tierId' weight to $weight.</green>",
                        withPrefix = true,
                    )
                }
            },
        )
        slot(
            31,
            EditorTileFactory.actionTile(
                material = Material.RED_DYE,
                title = "<red>Delete Selected Tier</red>",
                subtitle = "<gray>${draft.selectedTierId ?: "Select a tier first"}</gray>",
            ) { context ->
                val tierId = requireSelectedTier(context) ?: return@actionTile
                val result = draft.deleteSelectedTier()
                handleMutationResult(context, result, fallbackTierId = tierId) {
                    messageService()?.chatRaw(context.player, "<green>Deleted tier '$tierId'.</green>", withPrefix = true)
                }
            },
        )

        slot(32, StaticSlot(buildStateCard()))
        slot(33, StaticSlot(buildProbabilityCard()))
        slot(34, EditorTileFactory.actionTile(Material.EMERALD, "<green>Save Tier Changes</green>", "<gray>Writes tiers.* and prizes.*.tier</gray>") {
            saveDraft(it)
        })

        val refresh = ItemStack(Material.CLOCK)
        refresh.editMeta { meta -> meta.displayName(ComponentUtil.parse("<aqua>Refresh</aqua>")) }
        slot(35, StaticSlot(refresh))

        val back = ItemStack(Material.ARROW)
        back.editMeta { meta -> meta.displayName(ComponentUtil.parse("<gray>Back</gray>")) }
        slot(48, StaticSlot(back) { context -> EditorNavigationSupport.openGui(context, CrateEditorGui(crate)) })

        val close = ItemStack(Material.BARRIER)
        close.editMeta { meta -> meta.displayName(ComponentUtil.parse("<red>Close</red>")) }
        slot(49, StaticSlot(close) { it.session.closeRequested = true })
        fillEmptySlots { EditorBackgroundSupport.fillerSlot() }
    }

    private fun isTieredCrate(): Boolean =
        crate.selection.structure == SelectionStructure.TIERS

    private fun renderNotTieredState() {
        val unsupported = ItemStack(Material.BARRIER)
        unsupported.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<red>Tier Editing Unavailable</red>"))
            meta.lore(
                listOf(
                    "<gray>This crate is configured as:</gray>",
                    "<white>${crate.selection.structure.name}</white>",
                    "<dark_gray>Set selection.structure: TIERS to enable this editor.</dark_gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(unsupported))

        val back = ItemStack(Material.ARROW)
        back.editMeta { meta -> meta.displayName(ComponentUtil.parse("<gray>Back</gray>")) }
        slot(48, StaticSlot(back) { context -> EditorNavigationSupport.openGui(context, CrateEditorGui(crate)) })

        val close = ItemStack(Material.BARRIER)
        close.editMeta { meta -> meta.displayName(ComponentUtil.parse("<red>Close</red>")) }
        slot(49, StaticSlot(close) { it.session.closeRequested = true })
        fillEmptySlots { EditorBackgroundSupport.fillerSlot() }
    }

    private fun buildStateCard(): ItemStack {
        val selected = draft.selectedTier()
        val card = ItemStack(Material.WRITABLE_BOOK)
        card.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>Tier Editor State</gold>"))
            meta.lore(
                listOf(
                    "<gray>Dirty: <white>${draft.dirty}</white></gray>",
                    "<gray>Selected: <white>${draft.selectedTierId ?: "none"}</white></gray>",
                    "<gray>Name: <white>${selected?.displayName ?: "-"}</white></gray>",
                    "<gray>Weight: <white>${selected?.weight ?: "-"}</white></gray>",
                    "<gray>Reward refs: <white>${draft.rewardsUsingSelectedTier().size}</white></gray>",
                ).map(ComponentUtil::parse),
            )
        }
        return card
    }

    private fun buildProbabilityCard(): ItemStack {
        val card = ItemStack(Material.COMPASS)
        val tierId = draft.selectedTierId
        val chanceText = if (tierId != null && crate.tiers.containsKey(tierId)) {
            val table = ProbabilityEngine().buildTable(crate)
            val tierRows = table.rows.filter { it.tierId == tierId }
            ChanceFormatter.percent(tierRows.firstOrNull()?.tierChance ?: 0.0)
        } else {
            "n/a"
        }
        card.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<aqua>Runtime Probability Snapshot</aqua>"))
            meta.lore(
                listOf(
                    "<gray>Selected tier: <white>${tierId ?: "none"}</white></gray>",
                    "<gray>Effective chance: <white>$chanceText</white></gray>",
                    "<dark_gray>Reflects currently loaded crate until save/reload.</dark_gray>",
                ).map(ComponentUtil::parse),
            )
        }
        return card
    }

    private suspend fun prompt(context: GuiClickContext, prompt: String): String? =
        EditorPromptSupport.prompt(
            context = context,
            prompt = prompt,
            messageService = messageService(),
        )

    private fun requireSelectedTier(context: GuiClickContext): String? {
        val tierId = draft.selectedTierId
        if (tierId == null) {
            messageService()?.chatRaw(context.player, "<red>Select a tier first.</red>", withPrefix = true)
        }
        return tierId
    }

    private fun handleMutationResult(
        context: GuiClickContext,
        result: TierMutationSupport.MutationResult,
        fallbackTierId: String,
        onSuccess: () -> Unit,
    ) {
        if (result.success) {
            onSuccess()
            return
        }
        when (result.error) {
            TierMutationSupport.Error.UNKNOWN_TIER -> {
                messageService()?.chatRaw(context.player, "<red>Unknown tier '$fallbackTierId'.</red>", withPrefix = true)
            }
            TierMutationSupport.Error.TIER_ALREADY_EXISTS -> {
                messageService()?.chatRaw(context.player, "<red>Tier already exists.</red>", withPrefix = true)
            }
            TierMutationSupport.Error.TIER_REFERENCED -> {
                val refs = result.referencedRewards.joinToString(", ")
                messageService()?.chatRaw(
                    context.player,
                    "<red>Cannot delete tier '$fallbackTierId'; rewards reference it: $refs</red>",
                    withPrefix = true,
                )
            }
            TierMutationSupport.Error.INVALID_WEIGHT -> {
                messageService()?.chatRaw(context.player, "<red>Weight must be positive.</red>", withPrefix = true)
            }
            TierMutationSupport.Error.INVALID_TIER_ID -> {
                messageService()?.chatRaw(context.player, "<red>Tier id format is invalid.</red>", withPrefix = true)
            }
            null -> {
                messageService()?.chatRaw(context.player, "<red>Tier edit failed.</red>", withPrefix = true)
            }
        }
    }

    private suspend fun saveDraft(context: GuiClickContext) {
        if (!isTieredCrate()) {
            messageService()?.chatRaw(
                context.player,
                "<red>Crate '${crate.id}' is not configured for tiered selection.</red>",
                withPrefix = true,
            )
            return
        }
        val loaded = EditorCrateAccess.loadForEdit(crate.id)
        if (loaded == null) {
            messageService()?.chatRaw(context.player, "<red>Crate file for '${crate.id}' is missing.</red>", withPrefix = true)
            return
        }
        val mutability = EditorCrateAccess.mutability(loaded.crate)
        if (!mutability.allowed) {
            messageService()?.chatRaw(context.player, "<red>${mutability.warning ?: "This crate cannot be edited."}</red>", withPrefix = true)
            return
        }
        if (!draft.dirty) {
            messageService()?.chatRaw(context.player, "<gray>No tier changes to save.</gray>", withPrefix = true)
            return
        }

        draft.writeToYaml(loaded.yaml)
        val result = EditorCrateAccess.saveAndReload(loaded.file, loaded.yaml)
        if (!result.success) {
            messageService()?.chatRaw(
                context.player,
                "<red>${result.message ?: "Failed to save tier changes."}</red>",
                withPrefix = true,
            )
            return
        }
        val refreshed = EditorCrateAccess.findCrate(crate.id)
        if (refreshed != null) {
            EditorNavigationSupport.openGui(context, TierEditorGui(refreshed))
            messageService()?.chatRaw(context.player, "<green>Saved tier settings for '${crate.id}'.</green>", withPrefix = true)
        } else {
            messageService()?.chatRaw(context.player, "<red>Saved, but crate could not be reloaded into runtime.</red>", withPrefix = true)
        }
    }

    private fun messageService(): MessageService? =
        so.alaz.slate.api.SlateAPI.messageService()

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (!session.closeRequested) {
            // Keep tiles/state synchronized after every click and prompt.
            onBeforeOpen(context.player)
        }
    }
}

