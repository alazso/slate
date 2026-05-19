package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.command.sub.RewardMutationSupport
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.PrizeItem
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
 * Reward Editor — GUI-REFACTOR.md §11.
 *
 * 5-row layout:
 *   - Row 0 (slot 4): prize header
 *   - Row 1: NAME(9), TIER(10), WEIGHT(11), CHANCE(12), BROADCAST(14)
 *   - Row 2: separator
 *   - Row 3: items (27-29), commands (30-31), messages (32-33), grant summary (34)
 *   - Row 4: ADD-ITEM(36), ADD-CMD(37), ADD-MSG(38), ADD-GRANT(39), BACK(40), DELETE(43), SAVE(44)
 *
 * Uses an in-memory draft (`RewardEditorDraft`) so all mutations are previewed before Save
 * writes through to YAML and reloads the crate.
 */
class RewardEditorGui(
    private val crate: CrateDefinition,
    selectedRewardId: String? = null,
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_REWARD_EDITOR_TITLE, mapOf("crate" to crate.id)),
    rows = 5,
) {
    private val draft: RewardEditorDraft = RewardEditorDraft.fromCrate(crate, selectedRewardId)

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        for (i in 18..26) slot(i, border)

        val reward = draft.selectedReward()
        if (reward == null) {
            slot(22, emptyTile("No rewards configured."))
            slot(40, backTile())
            slot(36, addRewardTile())
            return
        }

        slot(4, headerTile(reward))
        slot(9, fieldTile(Material.NAME_TAG, "Name", reward.name) { promptName(it) })
        slot(10, fieldTile(Material.PAPER, "Tier", reward.tier ?: "none") { promptTier(it) })
        slot(11, fieldTile(Material.IRON_INGOT, "Weight", reward.weight.toString()) { promptWeight(it) })
        slot(12, fieldTile(Material.CLOCK, "Chance", reward.chance?.let { "${it}%" } ?: "(auto)") { promptChance(it) })
        slot(14, fieldTile(if (reward.broadcast) Material.BELL else Material.GRAY_DYE, "Broadcast", reward.broadcast.toString()) { toggleBroadcast(it) })

        renderRow3(reward)
        renderRow4()
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (session.closeRequested) return
        rebuild(context.player)
    }

    private fun headerTile(reward: RewardMutationSupport.RewardEntry): StaticSlot {
        val stack = reward.items.firstOrNull()?.let { ItemFactory.visualStackForPrizeItem(it, Material.PAPER) }
            ?: ItemStack(Material.PAPER)
        val placeholders = mapOf(
            "id" to reward.id,
            "name" to reward.name,
            "tier" to (reward.tier ?: "none"),
            "weight" to reward.weight,
            "items" to reward.items.size,
            "commands" to reward.commands.size,
            "messages" to reward.messages.size,
            "dirty" to draft.dirty,
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_REWARD_EDITOR_HEADER_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_REWARD_EDITOR_HEADER_LORE, placeholders))
        }
        return StaticSlot(stack)
    }

    private fun renderRow3(reward: RewardMutationSupport.RewardEntry) {
        val itemSlots = listOf(27, 28, 29)
        for ((i, prizeItem) in reward.items.take(itemSlots.size).withIndex()) {
            slot(itemSlots[i], itemTile(prizeItem, i))
        }
        val cmdSlots = listOf(30, 31)
        for ((i, cmd) in reward.commands.take(cmdSlots.size).withIndex()) {
            slot(cmdSlots[i], commandTile(cmd, i))
        }
        val msgSlots = listOf(32, 33)
        for ((i, msg) in reward.messages.take(msgSlots.size).withIndex()) {
            slot(msgSlots[i], messageTile(msg, i))
        }
        slot(34, grantSummaryTile(reward))
    }

    private fun renderRow4() {
        slot(36, actionTile(Material.LIME_DYE, "Add Item (from hand)", "messages.gui.reward_editor.add_item_lore") { addItemFromHand(it) })
        slot(37, actionTile(Material.COMMAND_BLOCK, "Add Command", "messages.gui.reward_editor.add_command_lore") { addCommand(it) })
        slot(38, actionTile(Material.PAPER, "Add Message", "messages.gui.reward_editor.add_message_lore") { addMessage(it) })
        slot(39, actionTile(Material.GOLD_INGOT, "Add Grant", "messages.gui.reward_editor.add_grant_lore") { addGrant(it) })
        slot(40, backTile())
        slot(43, actionTile(Material.RED_WOOL, "Delete Reward", "messages.gui.reward_editor.delete_lore") { confirmDelete(it) })
        slot(44, actionTile(Material.LIME_WOOL, "Save", "messages.gui.reward_editor.save_lore") { saveDraft(it) })
    }

    private suspend fun promptName(context: GuiClickContext) {
        val value = EditorPromptSupport.prompt(context, "Enter reward display name.", SlateAPI.messageService()) ?: return
        applyMutation(context, draft.setSelectedName(value))
    }

    private suspend fun promptTier(context: GuiClickContext) {
        val value = EditorPromptSupport.prompt(context, "Enter tier id (blank to clear).", SlateAPI.messageService()) ?: return
        applyMutation(context, draft.setSelectedTier(value.trim()))
    }

    private suspend fun promptWeight(context: GuiClickContext) {
        val raw = EditorPromptSupport.prompt(context, "Enter positive integer weight.", SlateAPI.messageService()) ?: return
        val parsed = raw.trim().toIntOrNull()
        if (parsed == null || parsed <= 0) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Weight must be a positive integer.</red>", withPrefix = true)
            return
        }
        applyMutation(context, draft.setSelectedWeight(parsed))
    }

    private suspend fun promptChance(context: GuiClickContext) {
        val raw = EditorPromptSupport.prompt(context, "Enter chance % (0.000001-100).", SlateAPI.messageService()) ?: return
        val parsed = raw.trim().toDoubleOrNull()
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid chance value.</red>", withPrefix = true)
            return
        }
        applyMutation(context, draft.setSelectedChance(parsed))
    }

    private suspend fun toggleBroadcast(context: GuiClickContext) {
        val current = draft.selectedReward()?.broadcast ?: false
        applyMutation(context, draft.setSelectedBroadcast(!current))
    }

    private suspend fun addItemFromHand(context: GuiClickContext) {
        val held = context.player.inventory.itemInMainHand
        if (held.type == Material.AIR) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Hold an item in your main hand first.</red>", withPrefix = true)
            return
        }
        val existing = draft.selectedReward()?.items ?: emptyList()
        val newItems = existing + PrizeItem(serialized = serializeItemStack(held))
        applyMutation(context, draft.setSelectedItems(newItems))
    }

    private suspend fun addCommand(context: GuiClickContext) {
        val value = EditorPromptSupport.prompt(context, "Enter command (no leading slash).", SlateAPI.messageService()) ?: return
        val existing = draft.selectedReward()?.commands ?: emptyList()
        applyMutation(context, draft.setSelectedCommands(existing + value.trim()))
    }

    private suspend fun addMessage(context: GuiClickContext) {
        val value = EditorPromptSupport.prompt(context, "Enter message line.", SlateAPI.messageService()) ?: return
        val existing = draft.selectedReward()?.messages ?: emptyList()
        applyMutation(context, draft.setSelectedMessages(existing + value))
    }

    private suspend fun addGrant(context: GuiClickContext) {
        GuiSounds.denied(context.player)
        CommandMessages.raw(
            context.player,
            "<yellow>Currency grants are configured via the YAML <gray>prizes.{id}.grants:</gray> list. Add them in /sla currency or edit the file directly.</yellow>",
            withPrefix = true,
        )
    }

    private suspend fun confirmDelete(context: GuiClickContext) {
        if (context.clickType != ClickType.SHIFT_LEFT && context.clickType != ClickType.SHIFT_RIGHT) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<yellow>Shift-click to confirm deletion.</yellow>", withPrefix = true)
            return
        }
        applyMutation(context, draft.deleteSelectedReward())
    }

    private suspend fun saveDraft(context: GuiClickContext) {
        val loaded = EditorCrateAccess.loadForEdit(crate.id)
        if (loaded == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Crate file is missing.</red>", withPrefix = true)
            return
        }
        val mutability = EditorCrateAccess.mutability(loaded.crate)
        if (!mutability.allowed) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>${mutability.warning ?: "Crate is immutable."}</red>", withPrefix = true)
            return
        }
        if (!draft.dirty) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<gray>Nothing to save.</gray>", withPrefix = true)
            return
        }
        draft.writeToYaml(loaded.yaml)
        val result = EditorCrateAccess.saveAndReload(loaded.file, loaded.yaml)
        if (!result.success) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>${result.message ?: "Save failed."}</red>", withPrefix = true)
            return
        }
        GuiSounds.success(context.player)
        SlateAPI.messageService()?.hotbar(context.player, MessageKeys.GUI_REWARD_EDITOR_SAVE_OK, mapOf("crate" to crate.id))
        val refreshed = EditorCrateAccess.findCrate(crate.id)
        if (refreshed != null) {
            EditorNavigationSupport.openGui(context, RewardEditorGui(refreshed, draft.selectedRewardId))
        }
    }

    private suspend fun addRewardTile(): StaticSlot = actionTile(Material.LIME_DYE, "Add Reward", "messages.gui.reward_editor.add_reward_lore") { context ->
        val rewardId = EditorPromptSupport.prompt(context, "Enter new reward id.", SlateAPI.messageService()) ?: return@actionTile
        applyMutation(context, draft.createReward(rewardId.trim()))
    }

    private fun blank(): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.editMeta { meta -> meta.displayName(net.kyori.adventure.text.Component.empty()) }
        return item
    }

    private fun fieldTile(material: Material, label: String, value: String, action: suspend (GuiClickContext) -> Unit): StaticSlot {
        val item = ItemStack(material)
        val placeholders = mapOf("label" to label, "value" to value)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_FIELD_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_EDITOR_FIELD_LORE, placeholders))
        }
        return StaticSlot(item) { ctx -> action(ctx) }
    }

    private fun actionTile(material: Material, label: String, loreKey: String, action: suspend (GuiClickContext) -> Unit): StaticSlot {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_REWARD_EDITOR_ACTION_NAME, mapOf("label" to label)))
            meta.lore(GuiText.lore(loreKey))
        }
        return StaticSlot(item) { ctx -> action(ctx) }
    }

    private fun itemTile(prizeItem: PrizeItem, index: Int): StaticSlot {
        val stack = ItemFactory.visualStackForPrizeItem(prizeItem, Material.CHEST)
        stack.editMeta { meta ->
            meta.lore(GuiText.lore(MessageKeys.GUI_REWARD_EDITOR_ITEM_LORE, mapOf("index" to index)))
        }
        return StaticSlot(stack) { ctx ->
            if (ctx.clickType == ClickType.SHIFT_RIGHT) {
                val updated = (draft.selectedReward()?.items ?: emptyList()).toMutableList()
                if (index < updated.size) {
                    updated.removeAt(index)
                    applyMutation(ctx, draft.setSelectedItems(updated))
                }
            }
        }
    }

    private fun commandTile(command: String, index: Int): StaticSlot {
        val item = ItemStack(Material.COMMAND_BLOCK)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_REWARD_EDITOR_COMMAND_NAME, mapOf("command" to command)))
            meta.lore(GuiText.lore(MessageKeys.GUI_REWARD_EDITOR_COMMAND_LORE, mapOf("index" to index)))
        }
        return StaticSlot(item) { ctx ->
            if (ctx.clickType == ClickType.SHIFT_RIGHT) {
                val updated = (draft.selectedReward()?.commands ?: emptyList()).toMutableList()
                if (index < updated.size) {
                    updated.removeAt(index)
                    applyMutation(ctx, draft.setSelectedCommands(updated))
                }
            }
        }
    }

    private fun messageTile(message: String, index: Int): StaticSlot {
        val item = ItemStack(Material.PAPER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_REWARD_EDITOR_MESSAGE_NAME, mapOf("message" to message)))
            meta.lore(GuiText.lore(MessageKeys.GUI_REWARD_EDITOR_MESSAGE_LORE, mapOf("index" to index)))
        }
        return StaticSlot(item) { ctx ->
            if (ctx.clickType == ClickType.SHIFT_RIGHT) {
                val updated = (draft.selectedReward()?.messages ?: emptyList()).toMutableList()
                if (index < updated.size) {
                    updated.removeAt(index)
                    applyMutation(ctx, draft.setSelectedMessages(updated))
                }
            }
        }
    }

    private fun grantSummaryTile(reward: RewardMutationSupport.RewardEntry): StaticSlot {
        val item = ItemStack(Material.GOLD_NUGGET)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_REWARD_EDITOR_GRANT_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_REWARD_EDITOR_GRANT_LORE))
        }
        return StaticSlot(item)
    }

    private fun emptyTile(text: String): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta -> meta.displayName(GuiText.component(MessageKeys.GUI_EDITOR_EMPTY_NAME, mapOf("text" to text))) }
        return StaticSlot(item)
    }

    private fun backTile(): StaticSlot {
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_BACK_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_BACK_LORE))
        }
        return StaticSlot(item) { ctx ->
            GuiSounds.navigate(ctx.player)
            EditorNavigationSupport.openGui(ctx, CrateEditorGui(crate, CrateEditorGui.Tab.REWARDS))
        }
    }

    private fun applyMutation(context: GuiClickContext, result: RewardMutationSupport.MutationResult) {
        if (result.success) {
            GuiSounds.click(context.player)
            return
        }
        GuiSounds.denied(context.player)
        val message = when (result.error) {
            RewardMutationSupport.Error.UNKNOWN_REWARD -> "Unknown reward."
            RewardMutationSupport.Error.REWARD_ALREADY_EXISTS -> "Reward id already exists."
            RewardMutationSupport.Error.INVALID_REWARD_ID -> "Invalid reward id."
            RewardMutationSupport.Error.INVALID_WEIGHT -> "Weight must be positive."
            RewardMutationSupport.Error.INVALID_CHANCE -> "Chance must be between 0 and 100."
            RewardMutationSupport.Error.INVALID_MODE -> "Field not valid for current selection mode."
            RewardMutationSupport.Error.INVALID_TIER -> "Unknown tier."
            RewardMutationSupport.Error.ITEMS_REQUIRED -> "At least one item entry required."
            null -> "Reward edit failed."
        }
        CommandMessages.raw(context.player, "<red>$message</red>", withPrefix = true)
    }

    private fun serializeItemStack(stack: ItemStack): String {
        val bytes = stack.serializeAsBytes()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
}
