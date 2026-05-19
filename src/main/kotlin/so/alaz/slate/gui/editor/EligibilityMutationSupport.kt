package so.alaz.slate.gui.editor

import so.alaz.slate.api.SlateAPI
import so.alaz.slate.gui.GuiClickContext

/**
 * Mutations for the crate "Eligibility" tab — permissions, region allow/deny, WorldGuard toggle.
 *
 * Stored under `requirements:` in the crate YAML.
 */
internal object EligibilityMutationSupport {

    enum class PermissionScope(val yamlKey: String) {
        OPEN("open"),
        PREVIEW("preview"),
        LIST("list"),
    }

    enum class RegionScope(val yamlKey: String) {
        ALLOW("allow"),
        DENY("deny"),
    }

    suspend fun promptSetPermissions(context: GuiClickContext, crateId: String, scope: PermissionScope): Boolean {
        val raw = EditorPromptSupport.prompt(
            context,
            "Enter comma-separated ${scope.name.lowercase()} permission nodes (blank to clear).",
            SlateAPI.messageService(),
        ) ?: return false
        val perms = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.eligibility.permissions_updated",
            successPlaceholders = mapOf("scope" to scope.name.lowercase(), "permissions" to perms.joinToString().ifBlank { "none" }),
        ) { _, yaml ->
            val path = "requirements.permissions.${scope.yamlKey}"
            if (perms.isEmpty()) yaml.set(path, null) else yaml.set(path, perms)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun promptSetRegions(context: GuiClickContext, crateId: String, scope: RegionScope): Boolean {
        val raw = EditorPromptSupport.prompt(
            context,
            "Enter comma-separated WorldGuard region ids for ${scope.name.lowercase()} (blank to clear).",
            SlateAPI.messageService(),
        ) ?: return false
        val regions = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.eligibility.regions_updated",
            successPlaceholders = mapOf("scope" to scope.name.lowercase(), "regions" to regions.joinToString().ifBlank { "none" }),
        ) { _, yaml ->
            val path = "requirements.regions.${scope.yamlKey}"
            if (regions.isEmpty()) yaml.set(path, null) else yaml.set(path, regions)
            EditorMutationSupport.MutationDecision.Apply
        }
    }

    suspend fun toggleRequireWorldGuard(context: GuiClickContext, crateId: String): Boolean {
        val crate = EditorCrateAccess.findCrate(crateId) ?: return false
        val current = crate.requirements.regions.requireWorldGuard
        val next = !current
        return EditorMutationSupport.applyMutation(
            context.player,
            crateId,
            successHotbarKey = "messages.gui.editor.eligibility.worldguard_toggled",
            successPlaceholders = mapOf("state" to if (next) "ON" else "OFF"),
        ) { _, yaml ->
            yaml.set("requirements.regions.require-worldguard", next)
            EditorMutationSupport.MutationDecision.Apply
        }
    }
}
