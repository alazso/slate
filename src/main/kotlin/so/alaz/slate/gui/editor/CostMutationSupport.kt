package so.alaz.slate.gui.editor

import org.bukkit.entity.Player
import org.bukkit.configuration.ConfigurationSection
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateCost
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSounds

/**
 * Mutations for the crate "Costs" tab.
 *
 * Stored YAML shape (under `costs:`):
 *   - type: currency
 *     provider: slate
 *     currency: coins
 *     amount: "1000"
 */
internal object CostMutationSupport {

    suspend fun promptAddCurrency(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(
            context,
            "Enter cost as provider:currency:amount (e.g. slate:coins:1000).",
            SlateAPI.messageService(),
        ) ?: return false
        val parts = raw.split(':')
        if (parts.size != 3) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Expected provider:currency:amount.</red>", withPrefix = true)
            return false
        }
        val provider = parts[0].trim()
        val currency = parts[1].trim()
        val amount = runCatching { CurrencyAmount.fromPersistedString(parts[2].trim()) }.getOrNull()
        if (provider.isBlank() || currency.isBlank() || amount == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid cost entry.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.costs.added",
            successPlaceholders = mapOf("entry" to "$provider:$currency:${amount.asPersistedString()}"),
        ) { _, yaml ->
            val existing = readCosts(yaml).toMutableList()
            existing.add(mapOf(
                "type" to "currency",
                "provider" to provider,
                "currency" to currency,
                "amount" to amount.asPersistedString(),
            ))
            writeCosts(yaml, existing)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun removeCost(context: GuiClickContext, crateId: String, index: Int): Boolean {
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.costs.removed",
            successPlaceholders = mapOf("index" to index),
        ) { _, yaml ->
            val existing = readCosts(yaml).toMutableList()
            if (index < 0 || index >= existing.size) {
                return@applyMutation EditorMutationSupport.MutationDecision.Abort("Cost index out of range.")
            }
            existing.removeAt(index)
            writeCosts(yaml, existing)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptEditAmount(context: GuiClickContext, crateId: String, index: Int): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter new amount (minor units).", SlateAPI.messageService()) ?: return false
        val amount = runCatching { CurrencyAmount.fromPersistedString(raw.trim()) }.getOrNull()
        if (amount == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid amount.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.costs.edited",
            successPlaceholders = mapOf("index" to index, "amount" to amount.asPersistedString()),
        ) { _, yaml ->
            val existing = readCosts(yaml).toMutableList()
            if (index < 0 || index >= existing.size) {
                return@applyMutation EditorMutationSupport.MutationDecision.Abort("Cost index out of range.")
            }
            val updated = existing[index].toMutableMap()
            updated["amount"] = amount.asPersistedString()
            existing[index] = updated
            writeCosts(yaml, existing)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    fun currencyCosts(costs: List<CrateCost>): List<CrateCost.CurrencyCost> =
        costs.filterIsInstance<CrateCost.CurrencyCost>()

    private fun readCosts(yaml: org.bukkit.configuration.file.YamlConfiguration): List<Map<String, Any?>> {
        val raw = yaml.getList("costs") ?: return emptyList()
        return raw.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> entry.entries.associate { (k, v) -> k.toString() to v }
                is ConfigurationSection -> entry.getValues(false)
                else -> null
            }
        }
    }

    private fun writeCosts(yaml: org.bukkit.configuration.file.YamlConfiguration, entries: List<Map<String, Any?>>) {
        if (entries.isEmpty()) yaml.set("costs", null) else yaml.set("costs", entries)
    }
}
