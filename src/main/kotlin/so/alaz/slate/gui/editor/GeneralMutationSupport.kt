package so.alaz.slate.gui.editor

import kotlin.time.Duration
import org.bukkit.entity.Player
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.crate.CrateState
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSounds

/**
 * Mutations for the crate "General" tab.
 *
 * Writes through to the underlying YAML and reloads the crate via `CrateManager`.
 */
internal object GeneralMutationSupport {

    suspend fun promptName(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter new crate name.", io.messageService()) ?: return false
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.name_updated",
            successPlaceholders = mapOf("name" to raw),
        ) { _, yaml ->
            yaml.set("name", raw)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun cycleState(context: GuiClickContext, crateId: String): Boolean {
        val crate = EditorCrateAccess.findCrate(crateId)
        if (crate == null) {
            GuiSounds.denied(context.player)
            return false
        }
        val states = CrateState.values()
        val nextState = states[(crate.lifecycle.state.ordinal + 1) % states.size]
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.state_updated",
            successPlaceholders = mapOf("state" to nextState.name),
        ) { _, yaml ->
            yaml.set("state", nextState.name.lowercase())
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptRequiredKeys(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter required key count.", io.messageService()) ?: return false
        val parsed = raw.trim().toIntOrNull()?.coerceAtLeast(1)
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid number.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.required_keys_updated",
            successPlaceholders = mapOf("amount" to parsed),
        ) { _, yaml ->
            yaml.set("required-keys", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptCooldown(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter cooldown duration (e.g. 30m, 1h, 5s).", io.messageService()) ?: return false
        val duration = parseDuration(raw.trim())
        if (duration == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid duration. Use e.g. 30s, 5m, 2h, 1d.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.cooldown_updated",
            successPlaceholders = mapOf("duration" to raw),
        ) { _, yaml ->
            yaml.set("cooldown", "${duration.inWholeMilliseconds}ms")
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptMaxPulls(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter max pulls (-1 for unlimited).", io.messageService()) ?: return false
        val parsed = raw.trim().toIntOrNull()
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid number.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.max_pulls_updated",
            successPlaceholders = mapOf("amount" to parsed),
        ) { _, yaml ->
            yaml.set("max-pulls", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptAllowedWorlds(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter comma-separated world names (blank = all).", io.messageService()) ?: return false
        val worlds = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.general.allowed_worlds_updated",
            successPlaceholders = mapOf("worlds" to (worlds.joinToString().ifBlank { "all" })),
        ) { _, yaml ->
            if (worlds.isEmpty()) yaml.set("allowed-worlds", null)
            else yaml.set("allowed-worlds", worlds)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    private fun parseDuration(input: String): Duration? = runCatching {
        Duration.parseOrNull(input) ?: parseShortForm(input)
    }.getOrNull()

    private fun parseShortForm(input: String): Duration? {
        val trimmed = input.trim().lowercase()
        val regex = Regex("(\\d+)(ms|s|m|h|d)")
        val matches = regex.findAll(trimmed).toList()
        if (matches.isEmpty()) return null
        var total = Duration.ZERO
        for (match in matches) {
            val value = match.groupValues[1].toLongOrNull() ?: return null
            total += when (match.groupValues[2]) {
                "ms" -> kotlin.time.Duration.parse("${value}ms")
                "s" -> kotlin.time.Duration.parse("${value}s")
                "m" -> kotlin.time.Duration.parse("${value}m")
                "h" -> kotlin.time.Duration.parse("${value}h")
                "d" -> kotlin.time.Duration.parse("${value}d")
                else -> return null
            }
        }
        return total
    }

    private val io = so.alaz.slate.api.SlateAPI
}
