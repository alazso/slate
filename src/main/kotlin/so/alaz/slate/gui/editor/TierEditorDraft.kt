package so.alaz.slate.gui.editor

import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.command.sub.TierMutationSupport
import so.alaz.slate.crate.CrateDefinition

internal data class TierEditorDraft(
    val state: TierMutationSupport.TierState,
    var selectedTierId: String?,
    var dirty: Boolean,
) {
    companion object {
        fun fromCrate(crate: CrateDefinition): TierEditorDraft {
            val state = TierMutationSupport.fromCrate(crate)
            return TierEditorDraft(
                state = state,
                selectedTierId = state.tiers.keys.firstOrNull(),
                dirty = false,
            )
        }
    }

    fun selectedTier(): TierMutationSupport.TierEntry? =
        selectedTierId?.let(state.tiers::get)

    fun rewardsUsingSelectedTier(): List<String> {
        val tierId = selectedTierId ?: return emptyList()
        return TierMutationSupport.rewardsUsingTier(state, tierId)
    }

    fun createTier(
        tierId: String,
        displayName: String = tierId,
        weight: Int = 1,
    ): TierMutationSupport.MutationResult {
        val result = TierMutationSupport.createTier(state, tierId, displayName, weight)
        if (result.success) {
            selectedTierId = tierId
            dirty = true
        }
        return result
    }

    fun renameSelectedTier(newTierId: String): TierMutationSupport.MutationResult {
        val oldTierId = selectedTierId ?: return TierMutationSupport.MutationResult(
            success = false,
            error = TierMutationSupport.Error.UNKNOWN_TIER,
        )
        val result = TierMutationSupport.renameTier(state, oldTierId, newTierId)
        if (result.success) {
            selectedTierId = newTierId
            dirty = true
        }
        return result
    }

    fun deleteSelectedTier(): TierMutationSupport.MutationResult {
        val tierId = selectedTierId ?: return TierMutationSupport.MutationResult(
            success = false,
            error = TierMutationSupport.Error.UNKNOWN_TIER,
        )
        val result = TierMutationSupport.deleteTier(state, tierId)
        if (result.success) {
            selectedTierId = state.tiers.keys.firstOrNull()
            dirty = true
        }
        return result
    }

    fun setSelectedTierName(displayName: String): TierMutationSupport.MutationResult {
        val tierId = selectedTierId ?: return TierMutationSupport.MutationResult(
            success = false,
            error = TierMutationSupport.Error.UNKNOWN_TIER,
        )
        val result = TierMutationSupport.setTierName(state, tierId, displayName)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedTierWeight(weight: Int): TierMutationSupport.MutationResult {
        val tierId = selectedTierId ?: return TierMutationSupport.MutationResult(
            success = false,
            error = TierMutationSupport.Error.UNKNOWN_TIER,
        )
        val result = TierMutationSupport.setTierWeight(state, tierId, weight)
        if (result.success) dirty = true
        return result
    }

    fun writeToYaml(yaml: YamlConfiguration) {
        TierMutationSupport.writeToYaml(state, yaml)
    }
}
