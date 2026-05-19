package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.admin.AdminActions
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageKeys

/**
 * Admin Panel — opened via `/sla editor`.
 *
 * Implements GUI-REFACTOR.md §8:
 *   - Slot 4: Slate header showing plugin metadata
 *   - Row 1: managers (Crate / Key / Currency / Anchor)
 *   - Row 3: ops (Health / Debug / Reload / Backup) — each invokes real services through AdminActions
 *   - Slot 53: close
 */
class AdminPanelGui : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_ADMIN_TITLE),
    rows = 6,
) {
    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val report = AdminActions.health()
        slot(4, headerTile(report))
        slot(10, managerTile(Material.CHEST, MessageKeys.GUI_ADMIN_CRATE_MANAGER_NAME, MessageKeys.GUI_ADMIN_CRATE_MANAGER_LORE) {
            SlateAPI.guiService()?.openGui(it, CrateManagerGui())
        })
        slot(12, managerTile(Material.TRIPWIRE_HOOK, MessageKeys.GUI_ADMIN_KEY_MANAGER_NAME, MessageKeys.GUI_ADMIN_KEY_MANAGER_LORE) {
            SlateAPI.guiService()?.openGui(it, KeyManagerGui())
        })
        slot(14, managerTile(Material.GOLD_INGOT, MessageKeys.GUI_ADMIN_CURRENCY_MANAGER_NAME, MessageKeys.GUI_ADMIN_CURRENCY_MANAGER_LORE) {
            SlateAPI.guiService()?.openGui(it, CurrencyManagerGui())
        })
        slot(16, managerTile(Material.LODESTONE, MessageKeys.GUI_ADMIN_ANCHOR_MANAGER_NAME, MessageKeys.GUI_ADMIN_ANCHOR_MANAGER_LORE) {
            SlateAPI.guiService()?.openGui(it, AnchorManagerGui())
        })
        slot(28, healthTile(report))
        slot(30, debugTile())
        slot(32, reloadTile())
        slot(34, backupTile())
        slot(53, closeTile())
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

    private fun headerTile(report: AdminActions.HealthReport): StaticSlot {
        val item = ItemStack(Material.NETHER_STAR)
        val placeholders = mapOf(
            "crateCount" to report.crateCount,
            "onlineCount" to report.onlineCount,
            "debug" to report.debug,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ADMIN_HEADER_NAME, placeholders))
            meta.lore(GuiText.lore(MessageKeys.GUI_ADMIN_HEADER_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun managerTile(
        material: Material,
        nameKey: String,
        loreKey: String,
        onClick: suspend (Player) -> Unit,
    ): StaticSlot {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(nameKey))
            meta.lore(GuiText.lore(loreKey))
        }
        return StaticSlot(item) { context ->
            GuiSounds.navigate(context.player)
            onClick(context.player)
        }
    }

    private fun healthTile(report: AdminActions.HealthReport): StaticSlot {
        val item = ItemStack(Material.EMERALD)
        val placeholders = mapOf("crateCount" to report.crateCount, "debug" to report.debug)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ADMIN_HEALTH_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ADMIN_HEALTH_LORE, placeholders))
        }
        return StaticSlot(item) { context ->
            GuiSounds.click(context.player)
            CommandMessages.chat(
                context.player,
                MessageKeys.HEALTH_SUMMARY,
                mapOf("crates" to report.crateCount, "debug" to report.debug),
            )
        }
    }

    private fun debugTile(): StaticSlot {
        val item = ItemStack(Material.REDSTONE_TORCH)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ADMIN_DEBUG_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ADMIN_DEBUG_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.click(context.player)
            CommandMessages.raw(context.player, "<gray>Use /sla debug for the full toolkit.</gray>", withPrefix = true)
        }
    }

    private fun reloadTile(): StaticSlot {
        val item = ItemStack(Material.COMMAND_BLOCK)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ADMIN_RELOAD_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ADMIN_RELOAD_LORE))
        }
        return StaticSlot(item) { context ->
            val result = AdminActions.reload()
            val summary = result.crateSummary
            if (summary == null) {
                GuiSounds.denied(context.player)
                CommandMessages.notInitialized(context.player)
            } else {
                GuiSounds.success(context.player)
                CommandMessages.chat(
                    context.player,
                    MessageKeys.GUI_ADMIN_RELOAD_RESULT,
                    mapOf(
                        "updated" to summary.filesUpdated,
                        "failed" to summary.filesFailed,
                    ),
                )
            }
        }
    }

    private fun backupTile(): StaticSlot {
        val item = ItemStack(Material.ENDER_CHEST)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_ADMIN_BACKUP_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_ADMIN_BACKUP_LORE))
        }
        return StaticSlot(item) { context ->
            when (val result = AdminActions.backup()) {
                is AdminActions.BackupResult.Created -> {
                    GuiSounds.success(context.player)
                    CommandMessages.chat(
                        context.player,
                        MessageKeys.GUI_ADMIN_BACKUP_RESULT,
                        mapOf("file" to result.file.fileName),
                    )
                }
                is AdminActions.BackupResult.SqliteOnly -> {
                    GuiSounds.denied(context.player)
                    CommandMessages.chat(context.player, MessageKeys.BACKUP_SQLITE_ONLY)
                }
                is AdminActions.BackupResult.DatabaseMissing -> {
                    GuiSounds.denied(context.player)
                    CommandMessages.chat(context.player, MessageKeys.BACKUP_DB_MISSING)
                }
                is AdminActions.BackupResult.Failed -> {
                    GuiSounds.denied(context.player)
                    CommandMessages.raw(
                        context.player,
                        "<red>Backup failed:</red> <gray>{error}</gray>",
                        mapOf("error" to (result.error.message ?: result.error.javaClass.simpleName)),
                        withPrefix = true,
                    )
                }
            }
        }
    }

    private fun closeTile(): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CONTROL_CLOSE_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CONTROL_CLOSE_LORE))
        }
        return StaticSlot(item) { context ->
            GuiSounds.click(context.player)
            context.session.closeRequested = true
        }
    }
}
