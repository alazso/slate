package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageService
import so.alaz.slate.util.ComponentUtil

class DisplayEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Display: ${crate.id}", 6) {
    private val draft = DisplayEditorDraft.fromCrate(crate)

    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        val stateCard = ItemStack(Material.ITEM_FRAME)
        stateCard.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>Display Settings</gold>"))
            meta.lore(
                listOf(
                    "<gray>Dirty: <white>${draft.dirty}</white></gray>",
                    "<gray>Enabled: <white>${draft.state.enabled}</white></gray>",
                    "<gray>View range: <white>${draft.state.viewRange}</white></gray>",
                    "<gray>Billboard: <white>${draft.state.billboard.name}</white></gray>",
                    "<gray>Line spacing: <white>${draft.state.lineSpacing}</white></gray>",
                    "<gray>Lines: <white>${draft.state.lines.size}</white></gray>",
                    "<gray>Item enabled: <white>${draft.state.item.enabled}</white></gray>",
                    "<gray>Item material: <white>${draft.state.item.material.name}</white></gray>",
                    "<gray>Item offset: <white>${draft.state.item.heightOffset}</white></gray>",
                    "<gray>Item scale: <white>${draft.state.item.scale}</white></gray>",
                    "<gray>Item transform: <white>${draft.state.item.transform.name}</white></gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(13, StaticSlot(stateCard))

        slot(19, EditorTileFactory.actionTile(Material.LEVER, "<yellow>Toggle Enabled</yellow>", "<gray>${draft.state.enabled}</gray>") {
            draft.setEnabled(!draft.state.enabled)
        })
        slot(20, EditorTileFactory.actionTile(Material.SPYGLASS, "<yellow>Edit View Range</yellow>", "<gray>${draft.state.viewRange}</gray>") { context ->
            val raw = prompt(context, "Enter view range (1-128).")
            val value = raw?.let(DisplayEditorInputParser::parseFloat)
            if (raw != null && value == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid view range.</red>", withPrefix = true)
                return@actionTile
            }
            if (value == null) return@actionTile
            val result = draft.setViewRange(value)
            if (!result.success) {
                messageService()?.chatRaw(context.player, "<red>View range must be positive.</red>", withPrefix = true)
            }
        })
        slot(21, EditorTileFactory.actionTile(Material.ENDER_EYE, "<yellow>Edit Billboard</yellow>", "<gray>${draft.state.billboard.name}</gray>") { context ->
            val raw = prompt(context, "Enter billboard: fixed|vertical|horizontal|center.")
            val billboard = raw?.let(DisplayEditorInputParser::parseBillboard)
            if (raw != null && billboard == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid billboard mode.</red>", withPrefix = true)
                return@actionTile
            }
            if (billboard != null) {
                draft.setBillboard(billboard)
            }
        })
        slot(22, EditorTileFactory.actionTile(Material.BOOKSHELF, "<yellow>Edit Lines</yellow>", "<gray>${draft.state.lines.size} lines</gray>") { context ->
            val raw = prompt(context, "Enter display lines separated by | (blank clears).")
            if (raw == null) return@actionTile
            draft.setLines(DisplayEditorInputParser.parseLines(raw))
        })
        slot(23, EditorTileFactory.actionTile(Material.STRING, "<yellow>Edit Line Spacing</yellow>", "<gray>${draft.state.lineSpacing}</gray>") { context ->
            val raw = prompt(context, "Enter line spacing (>0).")
            val value = raw?.let(DisplayEditorInputParser::parseDouble)
            if (raw != null && value == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid line spacing.</red>", withPrefix = true)
                return@actionTile
            }
            if (value == null) return@actionTile
            val result = draft.setLineSpacing(value)
            if (!result.success) {
                messageService()?.chatRaw(context.player, "<red>Line spacing must be positive.</red>", withPrefix = true)
            }
        })

        slot(28, EditorTileFactory.actionTile(Material.ITEM_FRAME, "<yellow>Toggle Item Display</yellow>", "<gray>${draft.state.item.enabled}</gray>") {
            draft.setItemEnabled(!draft.state.item.enabled)
        })
        slot(29, EditorTileFactory.actionTile(Material.CHEST, "<yellow>Edit Item Material</yellow>", "<gray>${draft.state.item.material.name}</gray>") { context ->
            val raw = prompt(context, "Enter item material.")
            val material = raw?.let(DisplayEditorInputParser::parseMaterial)
            if (raw != null && material == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid material.</red>", withPrefix = true)
                return@actionTile
            }
            if (material != null) {
                draft.setItemMaterial(material)
            }
        })
        slot(30, EditorTileFactory.actionTile(Material.FEATHER, "<yellow>Edit Item Height Offset</yellow>", "<gray>${draft.state.item.heightOffset}</gray>") { context ->
            val raw = prompt(context, "Enter item height offset.")
            val value = raw?.let(DisplayEditorInputParser::parseDouble)
            if (raw != null && value == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid number.</red>", withPrefix = true)
                return@actionTile
            }
            if (value != null) {
                draft.setItemHeightOffset(value)
            }
        })
        slot(31, EditorTileFactory.actionTile(Material.LIGHT_GRAY_DYE, "<yellow>Edit Item Scale</yellow>", "<gray>${draft.state.item.scale}</gray>") { context ->
            val raw = prompt(context, "Enter item scale (>0).")
            val value = raw?.let(DisplayEditorInputParser::parseFloat)
            if (raw != null && value == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid scale.</red>", withPrefix = true)
                return@actionTile
            }
            if (value == null) return@actionTile
            val result = draft.setItemScale(value)
            if (!result.success) {
                messageService()?.chatRaw(context.player, "<red>Scale must be positive.</red>", withPrefix = true)
            }
        })
        slot(32, EditorTileFactory.actionTile(Material.COMPASS, "<yellow>Edit Item Transform</yellow>", "<gray>${draft.state.item.transform.name}</gray>") { context ->
            val raw = prompt(context, "Enter transform (${ItemDisplay.ItemDisplayTransform.entries.joinToString(",") { it.name.lowercase() }}).")
            val transform = raw?.let(DisplayEditorInputParser::parseTransform)
            if (raw != null && transform == null) {
                messageService()?.chatRaw(context.player, "<red>Invalid transform.</red>", withPrefix = true)
                return@actionTile
            }
            if (transform != null) {
                draft.setItemTransform(transform)
            }
        })
        slot(33, EditorTileFactory.actionTile(Material.EMERALD, "<green>Save Display Changes</green>", "<gray>Writes display.*</gray>") {
            saveDraft(it)
        })

        val back = ItemStack(Material.ARROW)
        back.editMeta { meta -> meta.displayName(ComponentUtil.parse("<gray>Back</gray>")) }
        slot(48, StaticSlot(back) { context -> EditorNavigationSupport.openGui(context, CrateEditorGui(crate)) })

        val close = ItemStack(Material.BARRIER)
        close.editMeta { meta -> meta.displayName(ComponentUtil.parse("<red>Close</red>")) }
        slot(49, StaticSlot(close) { it.session.closeRequested = true })

        fillEmptySlots { EditorBackgroundSupport.fillerSlot() }
    }

    private suspend fun prompt(context: GuiClickContext, prompt: String): String? =
        EditorPromptSupport.prompt(context, prompt, messageService())

    private suspend fun saveDraft(context: GuiClickContext) {
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
            messageService()?.chatRaw(context.player, "<gray>No display changes to save.</gray>", withPrefix = true)
            return
        }

        draft.writeToYaml(loaded.yaml)
        val result = EditorCrateAccess.saveAndReload(loaded.file, loaded.yaml)
        if (!result.success) {
            messageService()?.chatRaw(context.player, "<red>${result.message ?: "Failed to save display changes."}</red>", withPrefix = true)
            return
        }
        so.alaz.slate.api.SlateAPI.displayService()?.reloadDisplays()
        val refreshed = EditorCrateAccess.findCrate(crate.id)
        if (refreshed != null) {
            EditorNavigationSupport.openGui(context, DisplayEditorGui(refreshed))
            messageService()?.chatRaw(context.player, "<green>Saved display settings for '${crate.id}'.</green>", withPrefix = true)
        } else {
            messageService()?.chatRaw(context.player, "<red>Saved, but crate could not be reloaded into runtime.</red>", withPrefix = true)
        }
    }

    private fun messageService(): MessageService? =
        so.alaz.slate.api.SlateAPI.messageService()

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        super.onClick(context, session, slot)
        if (!session.closeRequested) {
            onBeforeOpen(context.player)
        }
    }
}

