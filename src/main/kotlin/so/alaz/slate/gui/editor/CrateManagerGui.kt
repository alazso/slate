package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.admin.CrateAdminOperations
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateState
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

/**
 * Admin Crate Manager — GUI-REFACTOR.md §9.
 *
 *   - 7×3 paginated crate tiles
 *   - left-click: open editor / right-click: clone (prompts new id) / shift+right-click: delete (prompts confirm)
 *   - slot 36 Create New (prompts id)
 *   - slot 44 Validate All (runs ConfigValidator across every loaded crate)
 *   - slot 48/49/50 prev/page/next
 *   - slot 53 back to admin panel
 */
open class CrateManagerGui(
    private val backFactory: ((Player) -> SlateGui)? = { AdminPanelGui() },
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_CRATE_MANAGER_TITLE),
    rows = 6,
) {
    private var page: Int = 0

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val crates = EditorCrateAccess.listCrates()
        val totalPages = ((crates.size + GRID_SLOTS.size - 1) / GRID_SLOTS.size).coerceAtLeast(1)
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0
        val pageCrates = crates.drop(page * GRID_SLOTS.size).take(GRID_SLOTS.size)
        for ((index, crate) in pageCrates.withIndex()) {
            slot(GRID_SLOTS[index], crateTile(crate))
        }
        for (index in pageCrates.size until GRID_SLOTS.size) {
            slot(GRID_SLOTS[index], StaticSlot(blank()))
        }

        slot(36, createTile())
        slot(44, validateTile())
        slot(48, prevTile())
        slot(49, pageIndicator(totalPages, crates.size))
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

    private fun crateTile(crate: CrateDefinition): StaticSlot {
        val stack = ItemFactory.visualStackForCrateKey(crate, crate.keyDefinition.material)
        val tiers = crate.tiers.size
        val placeholders = mapOf(
            "name" to crate.name,
            "id" to crate.id,
            "state" to crate.lifecycle.state.name,
            "rewards" to crate.prizes.size,
            "tiers" to tiers,
            "mode" to crate.selection.mode.name,
        )
        stack.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_MANAGER_TILE_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_MANAGER_TILE_LORE, placeholders))
        }
        return StaticSlot(stack) { context ->
            handleCrateClick(context, crate)
        }
    }

    private suspend fun handleCrateClick(context: GuiClickContext, crate: CrateDefinition) {
        when (context.clickType) {
            ClickType.SHIFT_RIGHT -> {
                val input = EditorPromptSupport.prompt(
                    context,
                    "Type the crate id '${crate.id}' to confirm deletion, or 'cancel'.",
                    SlateAPI.messageService(),
                ) ?: return
                if (input != crate.id) {
                    GuiSounds.denied(context.player)
                    CommandMessages.raw(context.player, "<red>Confirmation did not match crate id.</red>", withPrefix = true)
                    return
                }
                handleResult(context, CrateAdminOperations.delete(crate.id), "Delete")
            }
            ClickType.RIGHT -> {
                val newId = EditorPromptSupport.prompt(
                    context,
                    "Enter a new crate id to clone '${crate.id}' into.",
                    SlateAPI.messageService(),
                ) ?: return
                handleResult(context, CrateAdminOperations.clone(crate.id, newId.trim()), "Clone")
            }
            else -> {
                GuiSounds.navigate(context.player)
                EditorNavigationSupport.openGui(context, CrateEditorGui(crate))
            }
        }
    }

    private fun createTile(): StaticSlot {
        val item = ItemStack(Material.LIME_CONCRETE)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_MANAGER_CREATE_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_MANAGER_CREATE_LORE))
        }
        return StaticSlot(item) { context ->
            val raw = EditorPromptSupport.prompt(
                context,
                "Enter the id for the new crate (letters, numbers, _, -).",
                SlateAPI.messageService(),
            ) ?: return@StaticSlot
            handleResult(context, CrateAdminOperations.createDefault(raw.trim()), "Create")
        }
    }

    private fun validateTile(): StaticSlot {
        val item = ItemStack(Material.WRITABLE_BOOK)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CRATE_MANAGER_VALIDATE_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CRATE_MANAGER_VALIDATE_LORE))
        }
        return StaticSlot(item) { context ->
            val summary = CrateAdminOperations.validateAll()
            if (summary.failed == 0) {
                GuiSounds.success(context.player)
                CommandMessages.chat(
                    context.player,
                    MessageKeys.GUI_CRATE_MANAGER_VALIDATE_OK,
                    mapOf("count" to summary.total),
                )
            } else {
                GuiSounds.denied(context.player)
                CommandMessages.chat(
                    context.player,
                    MessageKeys.GUI_CRATE_MANAGER_VALIDATE_FAIL,
                    mapOf("count" to summary.total, "fail" to summary.failed, "ids" to summary.failedIds.joinToString(", ")),
                )
            }
        }
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
        val item = ItemStack(Material.ARROW)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_BACK_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_BACK_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            if (factory != null) SlateAPI.guiService()?.openGui(context.player, factory(context.player))
            else context.session.closeRequested = true
        }
    }

    private fun handleResult(context: GuiClickContext, result: CrateAdminOperations.Result, action: String) {
        when (result) {
            is CrateAdminOperations.Result.Ok -> {
                GuiSounds.success(context.player)
                CommandMessages.raw(context.player, "<green>$action ok:</green> <gray>{id}</gray>", mapOf("id" to result.id), withPrefix = true)
            }
            is CrateAdminOperations.Result.AlreadyExists -> {
                GuiSounds.denied(context.player)
                CommandMessages.raw(context.player, "<red>Crate '{id}' already exists.</red>", mapOf("id" to result.id), withPrefix = true)
            }
            is CrateAdminOperations.Result.Missing -> {
                GuiSounds.denied(context.player)
                CommandMessages.raw(context.player, "<red>Crate '{id}' was not found.</red>", mapOf("id" to result.id), withPrefix = true)
            }
            is CrateAdminOperations.Result.ReloadFailed -> {
                GuiSounds.denied(context.player)
                CommandMessages.raw(context.player, "<red>Reload failed for '{id}': {reason}</red>", mapOf("id" to result.id, "reason" to (result.reason ?: "unknown")), withPrefix = true)
            }
            is CrateAdminOperations.Result.IoFailed -> {
                GuiSounds.denied(context.player)
                CommandMessages.raw(context.player, "<red>$action failed for '{id}': {error}</red>", mapOf("id" to result.id, "error" to (result.cause.message ?: result.cause.javaClass.simpleName)), withPrefix = true)
            }
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
