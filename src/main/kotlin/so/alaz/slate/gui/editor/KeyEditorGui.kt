package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageService
import so.alaz.slate.util.ComponentUtil

class KeyEditorGui(
    private val crate: CrateDefinition,
) : SlateGui("Key: ${crate.id}", 5) {
    private val draft = KeyEditorDraft.fromCrate(crate)
    private var dirty = false

    override suspend fun onBeforeOpen(player: org.bukkit.entity.Player) {
        slot(13, StaticSlot(buildPreviewItem(player)))
        slot(4, StaticSlot(buildStateCard()))

        slot(19, EditorTileFactory.actionTile(Material.NAME_TAG, "<yellow>Edit Name</yellow>", "<gray>${draft.name}</gray>") {
            val value = prompt(context = it, prompt = "Enter key name (MiniMessage). Type 'cancel' to abort.")
            if (value != null) {
                draft.name = value
                dirty = true
            }
        })
        slot(20, EditorTileFactory.actionTile(Material.BOOK, "<yellow>Edit Lore</yellow>", "<gray>Use | to split lines</gray>") {
            val value = prompt(context = it, prompt = "Enter key lore lines separated by |. Empty clears lore.")
            if (value != null) {
                draft.lore = KeyEditorInputParser.parseLore(value)
                dirty = true
            }
        })
        slot(21, EditorTileFactory.actionTile(Material.IRON_INGOT, "<yellow>Edit Material</yellow>", "<gray>${draft.material.name}</gray>") {
            val value = prompt(context = it, prompt = "Enter material (example: TRIPWIRE_HOOK).")
            if (value != null) {
                val material = KeyEditorInputParser.parseMaterial(value)
                if (material == null) {
                    messageService()?.chatRaw(it.player, "<red>Invalid material: $value</red>", withPrefix = true)
                } else {
                    draft.material = material
                    dirty = true
                }
            }
        })
        slot(22, EditorTileFactory.actionTile(Material.GLOWSTONE_DUST, "<yellow>Toggle Glowing</yellow>", "<gray>${draft.glowing}</gray>") {
            draft.glowing = !draft.glowing
            dirty = true
        })
        slot(23, EditorTileFactory.actionTile(Material.COMPASS, "<yellow>Cycle Provider</yellow>", "<gray>${draft.provider.providerId}</gray>") {
            draft.provider = nextProvider(draft.provider)
            dirty = true
        })
        slot(24, EditorTileFactory.actionTile(Material.PAPER, "<yellow>Edit Provider Item Id</yellow>", "<gray>${draft.providerItemId ?: "none"}</gray>") {
            val value = prompt(context = it, prompt = "Enter provider item id (blank clears).")
            if (value != null) {
                draft.providerItemId = value.ifBlank { null }
                dirty = true
            }
        })
        slot(25, EditorTileFactory.actionTile(Material.REPEATER, "<yellow>Edit Custom Model Data</yellow>", "<gray>${draft.customModelData.ifEmpty { listOf("none") }.joinToString(", ")}</gray>") {
            val value = prompt(context = it, prompt = "Enter custom model data floats separated by comma (blank clears).")
            if (value != null) {
                val parsed = KeyEditorInputParser.parseCustomModelData(value)
                if (parsed == null) {
                    messageService()?.chatRaw(it.player, "<red>Invalid model data. Use numeric values only.</red>", withPrefix = true)
                } else {
                    draft.customModelData = parsed
                    dirty = true
                }
            }
        })
        slot(28, EditorTileFactory.actionTile(Material.ITEM_FRAME, "<yellow>Edit Custom Item Model</yellow>", "<gray>${draft.customItemId ?: "none"}</gray>") {
            val value = prompt(context = it, prompt = "Enter custom item model key (blank clears).")
            if (value != null) {
                draft.customItemId = value.ifBlank { null }
                dirty = true
            }
        })
        slot(30, EditorTileFactory.actionTile(Material.LIME_DYE, "<green>Save Key Changes</green>", "<gray>Writes key.* YAML fields</gray>") {
            saveDraft(it)
        })

        val refresh = ItemStack(Material.CLOCK)
        refresh.editMeta { meta -> meta.displayName(ComponentUtil.parse("<aqua>Refresh Preview</aqua>")) }
        slot(31, StaticSlot(refresh))

        val back = ItemStack(Material.ARROW)
        back.editMeta { meta -> meta.displayName(ComponentUtil.parse("<gray>Back</gray>")) }
        slot(39, StaticSlot(back) { context -> EditorNavigationSupport.openGui(context, CrateEditorGui(crate)) })
        val close = ItemStack(Material.BARRIER)
        close.editMeta { meta -> meta.displayName(ComponentUtil.parse("<red>Close</red>")) }
        slot(40, StaticSlot(close) { it.session.closeRequested = true })
        fillEmptySlots { EditorBackgroundSupport.fillerSlot() }
    }

    private fun buildPreviewItem(player: org.bukkit.entity.Player): ItemStack {
        val preview = KeyEditorPreviewResolver.buildPreview(draft, player)
        preview.editMeta { meta ->
            val lore = (meta.lore()?.toMutableList() ?: mutableListOf())
            lore += ComponentUtil.parse("<dark_gray>--- Live Preview ---</dark_gray>")
            lore += ComponentUtil.parse("<gray>Admin context: <white>${player.name}</white></gray>")
            lore += ComponentUtil.parse("<gray>Provider: <white>${draft.provider.providerId}</white></gray>")
            lore += ComponentUtil.parse("<gray>Material: <white>${draft.material.name}</white></gray>")
            meta.lore(lore)
        }
        return preview
    }

    private fun buildStateCard(): ItemStack {
        val card = ItemStack(Material.WRITABLE_BOOK)
        card.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>Key Editor State</gold>"))
            meta.lore(
                listOf(
                    "<gray>Dirty: <white>$dirty</white></gray>",
                    "<gray>Provider: <white>${draft.provider.providerId}</white></gray>",
                    "<gray>Material: <white>${draft.material.name}</white></gray>",
                ).map(ComponentUtil::parse),
            )
        }
        return card
    }

    private suspend fun prompt(context: GuiClickContext, prompt: String): String? {
        return EditorPromptSupport.prompt(
            context = context,
            prompt = prompt,
            messageService = messageService(),
        )
    }

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
        if (!dirty) {
            messageService()?.chatRaw(context.player, "<gray>No key changes to save.</gray>", withPrefix = true)
            return
        }

        draft.writeToYaml(loaded.yaml)
        val result = EditorCrateAccess.saveAndReload(loaded.file, loaded.yaml)
        if (!result.success) {
            messageService()?.chatRaw(
                context.player,
                "<red>${result.message ?: "Failed to save key changes."}</red>",
                withPrefix = true,
            )
            return
        }
        val refreshed = EditorCrateAccess.findCrate(crate.id)
        if (refreshed != null) {
            dirty = false
            EditorNavigationSupport.openGui(context, KeyEditorGui(refreshed))
            messageService()?.chatRaw(context.player, "<green>Saved key settings for '${crate.id}'.</green>", withPrefix = true)
        } else {
            messageService()?.chatRaw(context.player, "<red>Saved, but crate could not be reloaded into runtime.</red>", withPrefix = true)
        }
    }

    private fun nextProvider(current: KeyProvider): KeyProvider {
        val values = KeyProvider.entries
        val idx = values.indexOf(current)
        return values[(idx + 1).mod(values.size)]
    }

    private fun messageService(): MessageService? =
        so.alaz.slate.api.SlateAPI.messageService()

    override suspend fun onClick(context: GuiClickContext, session: so.alaz.slate.gui.GuiSession, slot: so.alaz.slate.gui.GuiSlot?) {
        super.onClick(context, session, slot)
        if (!session.closeRequested) {
            // Keep live preview synchronized after each action or prompt outcome.
            onBeforeOpen(context.player)
        }
    }

}

