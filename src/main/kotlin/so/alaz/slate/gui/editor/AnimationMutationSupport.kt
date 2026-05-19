package so.alaz.slate.gui.editor

import org.bukkit.Sound
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSounds

/**
 * Mutations for the crate "Animation" tab.
 *
 * Stored under `animation:` in the crate YAML.
 */
internal object AnimationMutationSupport {

    private val TEMPLATES = listOf("csgo", "wheel", "instant", "reveal")

    suspend fun toggleEnabled(context: GuiClickContext, crateId: String): Boolean {
        val crate = EditorCrateAccess.findCrate(crateId) ?: return false
        val current = crate.animation?.enabled ?: false
        val next = !current
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.toggled",
            successPlaceholders = mapOf("state" to if (next) "ON" else "OFF"),
        ) { _, yaml ->
            yaml.set("animation.enabled", next)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun cycleTemplate(context: GuiClickContext, crateId: String): Boolean {
        val crate = EditorCrateAccess.findCrate(crateId) ?: return false
        val current = crate.animation?.template ?: TEMPLATES.first()
        val nextIndex = (TEMPLATES.indexOf(current).coerceAtLeast(0) + 1) % TEMPLATES.size
        val next = TEMPLATES[nextIndex]
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.template",
            successPlaceholders = mapOf("template" to next),
        ) { _, yaml ->
            yaml.set("animation.template", next)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptDurationTicks(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter animation duration in ticks.", SlateAPI.messageService()) ?: return false
        val parsed = raw.trim().toIntOrNull()?.coerceAtLeast(1)
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid duration.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.duration",
            successPlaceholders = mapOf("ticks" to parsed),
        ) { _, yaml ->
            yaml.set("animation.duration-ticks", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptTickInterval(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter animation tick interval.", SlateAPI.messageService()) ?: return false
        val parsed = raw.trim().toIntOrNull()?.coerceAtLeast(1)
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid tick interval.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.tick_interval",
            successPlaceholders = mapOf("ticks" to parsed),
        ) { _, yaml ->
            yaml.set("animation.tick-interval", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptDecoyCount(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter decoy count.", SlateAPI.messageService()) ?: return false
        val parsed = raw.trim().toIntOrNull()?.coerceAtLeast(0)
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid decoy count.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.decoys",
            successPlaceholders = mapOf("count" to parsed),
        ) { _, yaml ->
            yaml.set("animation.decoy-count", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptSpinSpeed(context: GuiClickContext, crateId: String): Boolean {
        val raw = EditorPromptSupport.prompt(context, "Enter spin speed multiplier (e.g. 1.0, 2.5).", SlateAPI.messageService()) ?: return false
        val parsed = raw.trim().toDoubleOrNull()?.coerceAtLeast(0.1)
        if (parsed == null) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Invalid number.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.spin_speed",
            successPlaceholders = mapOf("speed" to parsed),
        ) { _, yaml ->
            yaml.set("animation.spin-speed-multiplier", parsed)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptSound(context: GuiClickContext, crateId: String, which: SoundField): Boolean {
        val raw = EditorPromptSupport.prompt(
            context,
            "Enter sound name (e.g. UI_BUTTON_CLICK, blank to clear).",
            SlateAPI.messageService(),
        ) ?: return false
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && runCatching { Sound.valueOf(trimmed) }.isFailure) {
            GuiSounds.denied(context.player)
            CommandMessages.raw(context.player, "<red>Unknown sound: $trimmed.</red>", withPrefix = true)
            return false
        }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.animation.sound",
            successPlaceholders = mapOf("which" to which.name.lowercase(), "sound" to (trimmed.ifBlank { "none" })),
        ) { _, yaml ->
            val key = if (which == SoundField.TICK) "animation.sound-tick" else "animation.sound-reveal"
            yaml.set(key, trimmed.takeIf { it.isNotEmpty() })
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    enum class SoundField { TICK, REVEAL }
}
