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
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.economy.EconomyBalanceResult
import so.alaz.slate.economy.EconomyContext
import so.alaz.slate.economy.EconomyMutationResult
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.GuiSounds
import so.alaz.slate.gui.GuiText
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.message.MessageKeys

/**
 * Admin Currency Manager — GUI-REFACTOR.md §13.
 *
 *   - Slot 10: player selector (chat prompt)
 *   - Slot 12: provider selector (cycle left/right)
 *   - Slot 14: currency selector (cycle within the selected provider)
 *   - Slots 19/21/23/25: give/take/set/balance actions (chat prompts via fromPersistedString)
 *   - Slot 31: live balance for player+provider+currency
 *   - Slot 40: back
 */
class CurrencyManagerGui(
    private val backFactory: ((Player) -> SlateGui)? = { AdminPanelGui() },
) : SlateGui(
    title = GuiText.raw(MessageKeys.GUI_CURRENCY_MANAGER_TITLE),
    rows = 5,
) {
    private var selectedPlayerId: UUID? = null
    private var selectedPlayerName: String? = null
    private var providerIndex: Int = 0
    private var currencyIndex: Int = 0
    private var balanceCache: String = "—"

    override suspend fun onBeforeOpen(player: Player) {
        val border = StaticSlot(blank())
        fillBorder(border)

        val economy = SlateAPI.economyService()
        if (economy == null) {
            slot(22, missingTile())
            slot(40, backTile())
            return
        }
        val providers = economy.providers()
        if (providers.isEmpty()) {
            slot(22, missingTile())
            slot(40, backTile())
            return
        }
        if (providerIndex >= providers.size) providerIndex = 0
        val provider = providers[providerIndex]
        val currencies = provider.currencies().toList()
        if (currencies.isEmpty()) {
            slot(10, playerTile())
            slot(12, providerTile(provider.id))
            slot(22, missingCurrencyTile(provider.id))
            slot(40, backTile())
            return
        }
        if (currencyIndex >= currencies.size) currencyIndex = 0
        val currency = currencies[currencyIndex]
        val pid = selectedPlayerId
        balanceCache = if (pid != null) {
            val result = economy.getBalance(CurrencyAccount.player(pid), provider.id, currency.id)
            when (result) {
                is EconomyBalanceResult.Success -> economy.formatAmount(result.balance, provider.id, currency.id)
                else -> "—"
            }
        } else "—"

        slot(10, playerTile())
        slot(12, providerTile(provider.id))
        slot(14, currencyTile(currency.id, currency.name))
        slot(19, mutationTile(Material.LIME_DYE, MessageKeys.GUI_CURRENCY_MANAGER_GIVE_NAME, MessageKeys.GUI_CURRENCY_MANAGER_GIVE_LORE, MutationKind.GIVE))
        slot(21, mutationTile(Material.YELLOW_DYE, MessageKeys.GUI_CURRENCY_MANAGER_TAKE_NAME, MessageKeys.GUI_CURRENCY_MANAGER_TAKE_LORE, MutationKind.TAKE))
        slot(23, mutationTile(Material.ORANGE_DYE, MessageKeys.GUI_CURRENCY_MANAGER_SET_NAME, MessageKeys.GUI_CURRENCY_MANAGER_SET_LORE, MutationKind.SET))
        slot(25, balanceTile(provider.id, currency.id))
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
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_PLAYER_NAME, mapOf("player" to name)))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_PLAYER_LORE))
            if (meta is org.bukkit.inventory.meta.SkullMeta && selectedPlayerId != null) {
                meta.owningPlayer = Bukkit.getOfflinePlayer(selectedPlayerId!!)
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

    private fun providerTile(providerId: String): StaticSlot {
        val item = ItemStack(Material.ENDER_CHEST)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_PROVIDER_NAME, mapOf("provider" to providerId)))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_PROVIDER_LORE))
        }
        return StaticSlot(item) { context ->
            val providers = SlateAPI.economyService()?.providers().orEmpty()
            if (providers.isEmpty()) return@StaticSlot
            providerIndex = if (context.clickType == ClickType.RIGHT) {
                ((providerIndex - 1) + providers.size) % providers.size
            } else {
                (providerIndex + 1) % providers.size
            }
            currencyIndex = 0
            GuiSounds.click(context.player)
        }
    }

    private fun currencyTile(currencyId: String, currencyName: String): StaticSlot {
        val item = ItemStack(Material.GOLD_INGOT)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_CURRENCY_NAME, mapOf("currency" to currencyName, "id" to currencyId)))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_CURRENCY_LORE))
        }
        return StaticSlot(item) { context ->
            val economy = SlateAPI.economyService() ?: return@StaticSlot
            val provider = economy.providers().getOrNull(providerIndex) ?: return@StaticSlot
            val list = provider.currencies().toList()
            if (list.isEmpty()) return@StaticSlot
            currencyIndex = if (context.clickType == ClickType.RIGHT) {
                ((currencyIndex - 1) + list.size) % list.size
            } else {
                (currencyIndex + 1) % list.size
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
        val economy = SlateAPI.economyService() ?: return
        val provider = economy.providers().getOrNull(providerIndex) ?: return
        val currency = provider.currencies().toList().getOrNull(currencyIndex) ?: return
        val scope = SlateAPI.pluginScope() ?: return

        val raw = EditorPromptSupport.prompt(
            context,
            "Enter ${kind.label.lowercase()} amount (minor units) for ${currency.id}.",
            SlateAPI.messageService(),
        ) ?: return
        val amount = runCatching { CurrencyAmount.fromPersistedString(raw.trim()) }.getOrNull()
        if (amount == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid amount.</red>", withPrefix = true)
            return
        }

        val ctx = EconomyContext(reason = "admin.gui.currency.${kind.label.lowercase()}")
        val account = CurrencyAccount.player(playerId)
        val scheduler = SlateAPI.scheduler() ?: return
        scope.launch {
            val result: EconomyMutationResult = when (kind) {
                MutationKind.GIVE -> economy.deposit(account, provider.id, currency.id, amount, ctx)
                MutationKind.TAKE -> economy.withdraw(account, provider.id, currency.id, amount, ctx)
                MutationKind.SET -> economy.setBalance(account, provider.id, currency.id, amount, ctx)
            }
            rebuildAndRefresh(context.player, scheduler, context.session.inventory)
            CommandMessages.dispatchForSender(context.player) {
                when (result) {
                    is EconomyMutationResult.Success -> {
                        GuiSounds.success(context.player)
                        val after = result.afterBalance?.let { economy.formatAmount(it, provider.id, currency.id) } ?: "?"
                        CommandMessages.chat(
                            context.player,
                            MessageKeys.GUI_CURRENCY_MANAGER_RESULT,
                            mapOf("action" to kind.label, "result" to "${currency.id} now $after"),
                        )
                    }
                    else -> {
                        GuiSounds.denied(context.player)
                        CommandMessages.chat(
                            context.player,
                            MessageKeys.GUI_CURRENCY_MANAGER_RESULT,
                            mapOf("action" to kind.label, "result" to result.toString()),
                        )
                    }
                }
            }
        }
    }

    private fun balanceTile(providerId: String, currencyId: String): StaticSlot {
        val item = ItemStack(Material.PAPER)
        val placeholders = mapOf(
            "player" to (selectedPlayerName ?: "—"),
            "provider" to providerId,
            "currency" to currencyId,
            "balance" to balanceCache,
        )
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_BALANCE_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_BALANCE_LORE, placeholders))
        }
        return StaticSlot(item)
    }

    private fun missingTile(): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_NO_PROVIDER_NAME))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_NO_PROVIDER_LORE))
        }
        return StaticSlot(item)
    }

    private fun missingCurrencyTile(providerId: String): StaticSlot {
        val item = ItemStack(Material.BARRIER)
        item.editMeta { meta ->
            meta.displayName(GuiText.component(MessageKeys.GUI_CURRENCY_MANAGER_NO_CURRENCY_NAME, mapOf("provider" to providerId)))
            meta.lore(GuiText.lore(MessageKeys.GUI_CURRENCY_MANAGER_NO_CURRENCY_LORE))
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
    }
}
