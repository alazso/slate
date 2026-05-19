package so.alaz.slate.gui.editor

import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.command.sub.ProbabilityCommandSupport
import so.alaz.slate.gui.GuiSounds

/**
 * Shared utilities for crate editor mutation flows.
 *
 * Each mutation support (general/cost/eligibility/animation) calls into this object to load the
 * editable YAML, apply changes, persist, reload the crate, and surface success/failure feedback.
 */
internal object EditorMutationSupport {

    inline fun applyMutation(
        player: Player,
        crateId: String,
        successHotbarKey: String? = null,
        successPlaceholders: Map<String, Any?> = emptyMap(),
        block: (file: java.io.File, yaml: org.bukkit.configuration.file.YamlConfiguration) -> MutationDecision,
    ): Boolean {
        val editable = EditorCrateAccess.loadForEdit(crateId)
        if (editable == null) {
            GuiSounds.denied(player)
            CommandMessages.raw(player, "<red>Crate file for $crateId could not be loaded.</red>", withPrefix = true)
            return false
        }
        val mutability = EditorCrateAccess.mutability(editable.crate)
        if (!mutability.allowed) {
            GuiSounds.denied(player)
            CommandMessages.raw(player, "<red>${mutability.warning ?: "Crate is immutable."}</red>", withPrefix = true)
            return false
        }
        val decision = block(editable.file, editable.yaml)
        if (decision is MutationDecision.Abort) {
            if (decision.warningMessage != null) {
                GuiSounds.denied(player)
                CommandMessages.raw(player, "<red>${decision.warningMessage}</red>", withPrefix = true)
            }
            return false
        }
        val result: ProbabilityCommandSupport.SaveReloadResult =
            EditorCrateAccess.saveAndReload(editable.file, editable.yaml)
        if (!result.success) {
            GuiSounds.denied(player)
            CommandMessages.raw(player, "<red>${result.message ?: "Save failed."}</red>", withPrefix = true)
            return false
        }
        GuiSounds.success(player)
        if (successHotbarKey != null) {
            SlateAPI.messageService()?.hotbar(player, successHotbarKey, successPlaceholders)
        }
        return true
    }

    sealed interface MutationDecision {
        data object Apply : MutationDecision
        data class Abort(val warningMessage: String? = null) : MutationDecision
    }
}
