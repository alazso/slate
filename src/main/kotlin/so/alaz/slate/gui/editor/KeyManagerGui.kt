package so.alaz.slate.gui.editor

import java.util.UUID
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.command.sub.CommandTargetResolver
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageKeys

/**
 * Admin Key Manager — GUI-REFACTOR.md §12.
 *
 *   - Slot 10: player selector (chat prompt for name, resolved via CommandTargetResolver)
 *   - Slot 12: crate selector (left-click cycles forwards, right-click cycles backwards)
 *   - Slots 19/21/23/25: give/take/set/clear actions (chat prompt amount; clear is shift-click)
 *   - Slot 31: live balance for the selected player+crate
 *   - Slot 40: back
 *
 * All mutations go through `keyService` directly, so the GUI shares the same code path as `/sla key`.
 */
class KeyManagerGui(
    private val backFactory: ((Player) -> SlateGui)? = { AdminPanelGui() },
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_KEY_MANAGER_TITLE),
    rows = 5,
) {
    private var selectedPlayerId: UUID? = null
    private var selectedPlayerName: String? = null
    private var crateIndex: Int = 0
    private var balanceCache: Int = 0

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val crates = SlateAPI.crateManager()?.all()?.sortedBy { it.id } ?: emptyList()
        if (crates.isEmpty()) {
            slot(22, emptyTile())
            slot(40, backTile())
            return
        }
        if (crateIndex >= crates.size) crateIndex = 0
        val crate = crates[crateIndex]
        val keyService = SlateAPI.keyService()
        val playerId = selectedPlayerId
        balanceCache = if (playerId != null && keyService != null) keyService.getVirtualKeys(playerId, crate.id) else 0

        slot(10, playerTile())
        slot(12, crateTile(crate.id))
        slot(19, mutationTile(Material.LIME_DYE, MessageKeys.GUI_KEY_MANAGER_GIVE_NAME, MessageKeys.GUI_KEY_MANAGER_GIVE_LORE, MutationKind.GIVE))
        slot(21, mutationTile(Material.YELLOW_DYE, MessageKeys.GUI_KEY_MANAGER_TAKE_NAME, MessageKeys.GUI_KEY_MANAGER_TAKE_LORE, MutationKind.TAKE))
        slot(23, mutationTile(Material.ORANGE_DYE, MessageKeys.GUI_KEY_MANAGER_SET_NAME, MessageKeys.GUI_KEY_MANAGER_SET_LORE, MutationKind.SET))
        slot(25, mutationTile(Material.RED_DYE, MessageKeys.GUI_KEY_MANAGER_CLEAR_NAME, MessageKeys.GUI_KEY_MANAGER_CLEAR_LORE, MutationKind.CLEAR))
        slot(31, balanceTile(crate.id))
        slot(40, backTile())
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

    private fun playerTile(): StaticSlot {
        val item = ItemStack(Material.PLAYER_HEAD)
        val name = selectedPlayerName ?: "—"
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_KEY_MANAGER_PLAYER_NAME, mapOf("player" to name)))
            meta.lore(GuiText.lore(MessageKeys.GUI_KEY_MANAGER_PLAYER_LORE))
            if (meta is org.bukkit.inventory.meta.SkullMeta && selectedPlayerId != null) {
                val offline = Bukkit.getOfflinePlayer(selectedPlayerId!!)
                meta.owningPlayer = offline
            }
        }
        return StaticSlot(item) { context ->
            val raw = EditorPromptSupport.prompt(context, "Enter player name to manage.", SlateAPI.messageService())
                ?: return@StaticSlot
            val uuid = CommandTargetResolver.resolveUuid(raw.trim())
            if (uuid == null) {
                GuiSounds.denied(context.player)
                CommandMessages.chat(context.player, MessageKeys.KEYS_PLAYER_NOT_FOUND)
            } else {
                selectedPlayerId = uuid
                selectedPlayerName = Bukkit.getOfflinePlayer(uuid).name ?: raw.trim()
                GuiSounds.click(context.player)
            }
        }
    }

    private fun crateTile(crateId: String): StaticSlot {
        val item = ItemStack(Material.CHEST)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_KEY_MANAGER_CRATE_NAME, mapOf("crate" to crateId)))
            meta.lore(GuiText.lore(MessageKeys.GUI_KEY_MANAGER_CRATE_LORE))
        }
        return StaticSlot(item) { context ->
            val crates = SlateAPI.crateManager()?.all()?.sortedBy { it.id } ?: return@StaticSlot
            crateIndex = if (context.clickType == ClickType.RIGHT) {
                ((crateIndex - 1) + crates.size) % crates.size
            } else {
                (crateIndex + 1) % crates.size
            }
            GuiSounds.click(context.player)
        }
    }

    private fun mutationTile(material: Material, nameKey: String, loreKey: String, kind: MutationKind): StaticSlot {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(nameKey))
            meta.lore(GuiText.lore(loreKey))
        }
        return StaticSlot(item) { context -> performMutation(context, kind) }
    }

    private suspend fun performMutation(context: GuiClickContext, kind: MutationKind) {
        val playerId = selectedPlayerId
        if (playerId == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Select a player first.</red>", withPrefix = true)
            return
        }
        val crates = SlateAPI.crateManager()?.all()?.sortedBy { it.id } ?: return
        if (crates.isEmpty()) return
        val crateId = crates[crateIndex].id
        val keyService = SlateAPI.keyService() ?: return
        val scope = SlateAPI.pluginScope() ?: return

        val amount = when (kind) {
            MutationKind.CLEAR -> 0
            else -> {
                val raw = EditorPromptSupport.prompt(
                    context,
                    "Enter ${kind.label.lowercase()} amount for $crateId.",
                    SlateAPI.messageService(),
                ) ?: return
                raw.trim().toIntOrNull() ?: run {
                    GuiSounds.denied(context.player)
                    CommandMessages.raw(context.player, "<red>Invalid number.</red>", withPrefix = true)
                    return
                }
            }
        }

        val scheduler = SlateAPI.scheduler() ?: return
        scope.launch {
            val result = runCatching {
                when (kind) {
                    MutationKind.GIVE -> keyService.giveVirtualKeys(playerId, crateId, amount, silent = true)
                    MutationKind.TAKE -> keyService.takeVirtualKeys(playerId, crateId, amount)
                    MutationKind.SET -> keyService.setVirtualKeys(playerId, crateId, amount)
                    MutationKind.CLEAR -> keyService.setVirtualKeys(playerId, crateId, 0)
                }
            }
            val finalAmount = runCatching { keyService.getVirtualKeys(playerId, crateId) }.getOrDefault(0)
            rebuildAndRefresh(context.player, scheduler, context.session.inventory)
            CommandMessages.dispatchForSender(context.player) {
                result.onSuccess {
                    GuiSounds.success(context.player)
                    CommandMessages.chat(
                        context.player,
                        MessageKeys.GUI_KEY_MANAGER_RESULT,
                        mapOf("action" to kind.label, "result" to "$crateId now $finalAmount"),
                    )
                }.onFailure { err ->
                    GuiSounds.denied(context.player)
                    CommandMessages.chat(
                        context.player,
                        MessageKeys.KEYS_OPERATION_FAILED,
                        mapOf("operation" to kind.label.lowercase(), "error" to (err.message ?: "error")),
                    )
                }
            }
        }
    }

    private fun balanceTile(crateId: String): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf(
            "player" to (selectedPlayerName ?: "—"),
            "crate" to crateId,
            "amount" to balanceCache,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_KEY_MANAGER_BALANCE_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_KEY_MANAGER_BALANCE_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun emptyTile(): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.COMMON_UNKNOWN_CRATE, mapOf("crate" to "none")))
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

    private enum class MutationKind(val label: String) {
        GIVE("Give"),
        TAKE("Take"),
        SET("Set"),
        CLEAR("Clear"),
    }
}
