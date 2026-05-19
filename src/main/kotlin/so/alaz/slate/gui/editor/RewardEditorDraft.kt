package so.alaz.slate.gui.editor

import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.command.sub.RewardMutationSupport
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.PrizeItem

internal data class RewardEditorDraft(
    val crate: CrateDefinition,
    val state: RewardMutationSupport.RewardState,
    var selectedRewardId: String?,
    var dirty: Boolean,
) {
    companion object {
        fun fromCrate(crate: CrateDefinition, selectedRewardId: String? = null): RewardEditorDraft {
            val state = RewardMutationSupport.fromCrate(crate)
            return RewardEditorDraft(
                crate = crate,
                state = state,
                selectedRewardId = selectedRewardId ?: state.rewards.keys.firstOrNull(),
                dirty = false,
            )
        }
    }

    fun selectedReward(): RewardMutationSupport.RewardEntry? =
        selectedRewardId?.let(state.rewards::get)

    fun createReward(rewardId: String): RewardMutationSupport.MutationResult {
        val result = RewardMutationSupport.createReward(state, crate, rewardId)
        if (result.success) {
            selectedRewardId = rewardId
            dirty = true
        }
        return result
    }

    fun deleteSelectedReward(): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(
            success = false,
            error = RewardMutationSupport.Error.UNKNOWN_REWARD,
        )
        val result = RewardMutationSupport.deleteReward(state, rewardId)
        if (result.success) {
            selectedRewardId = state.rewards.keys.firstOrNull()
            dirty = true
        }
        return result
    }

    fun renameSelectedReward(newRewardId: String): RewardMutationSupport.MutationResult {
        val oldRewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(
            success = false,
            error = RewardMutationSupport.Error.UNKNOWN_REWARD,
        )
        val result = RewardMutationSupport.renameReward(state, oldRewardId, newRewardId)
        if (result.success) {
            selectedRewardId = newRewardId
            dirty = true
        }
        return result
    }

    fun setSelectedName(value: String): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setRewardName(state, rewardId, value)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedTier(value: String): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setRewardTier(state, crate, rewardId, value)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedWeight(value: Int): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setRewardWeight(state, crate, rewardId, value)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedChance(value: Double): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setRewardChance(state, crate, rewardId, value)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedBroadcast(enabled: Boolean): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setBroadcast(state, rewardId, enabled)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedBroadcastMessage(value: String): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setBroadcastMessage(state, rewardId, value)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedItems(items: List<PrizeItem>): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setItems(state, rewardId, items)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedCommands(commands: List<String>): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setCommands(state, rewardId, commands)
        if (result.success) dirty = true
        return result
    }

    fun setSelectedMessages(messages: List<String>): RewardMutationSupport.MutationResult {
        val rewardId = selectedRewardId ?: return RewardMutationSupport.MutationResult(false, RewardMutationSupport.Error.UNKNOWN_REWARD)
        val result = RewardMutationSupport.setMessages(state, rewardId, messages)
        if (result.success) dirty = true
        return result
    }

    fun writeToYaml(yaml: YamlConfiguration) {
        RewardMutationSupport.writeToYaml(state, yaml)
    }
}
