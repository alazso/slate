package so.alaz.slate.command.sub

import java.util.LinkedHashMap
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.crate.CrateDefinition

internal object TierMutationSupport {
    data class TierEntry(
        var displayName: String,
        var weight: Int,
    )

    data class TierState(
        val tiers: LinkedHashMap<String, TierEntry>,
        val rewardTierAssignments: LinkedHashMap<String, String?>,
    )

    enum class Error {
        UNKNOWN_TIER,
        TIER_ALREADY_EXISTS,
        TIER_REFERENCED,
        INVALID_WEIGHT,
        INVALID_TIER_ID,
    }

    data class MutationResult(
        val success: Boolean,
        val error: Error? = null,
        val referencedRewards: List<String> = emptyList(),
    )

    fun fromCrate(crate: CrateDefinition): TierState =
        TierState(
            tiers = LinkedHashMap(
                crate.tiers
                    .toSortedMap()
                    .mapValues { (_, tier) -> TierEntry(displayName = tier.displayName, weight = tier.weight) },
            ),
            rewardTierAssignments = LinkedHashMap(
                crate.prizes
                    .sortedBy { it.id }
                    .associate { it.id to it.tier },
            ),
        )

    fun fromYaml(yaml: YamlConfiguration): TierState {
        val tiers = LinkedHashMap<String, TierEntry>()
        yaml.getConfigurationSection("tiers")
            ?.getKeys(false)
            ?.sorted()
            ?.forEach { tierId ->
                tiers[tierId] = TierEntry(
                    displayName = yaml.getString("tiers.$tierId.name", tierId).orEmpty(),
                    weight = yaml.getInt("tiers.$tierId.weight", 1),
                )
            }

        val rewardAssignments = LinkedHashMap<String, String?>()
        yaml.getConfigurationSection("prizes")
            ?.getKeys(false)
            ?.sorted()
            ?.forEach { rewardId ->
                rewardAssignments[rewardId] = yaml.getString("prizes.$rewardId.tier")
            }

        return TierState(tiers = tiers, rewardTierAssignments = rewardAssignments)
    }

    fun createTier(
        state: TierState,
        tierId: String,
        displayName: String = tierId,
        weight: Int = 1,
    ): MutationResult {
        if (tierId.isBlank()) return MutationResult(success = false, error = Error.INVALID_TIER_ID)
        if (weight < 1) return MutationResult(success = false, error = Error.INVALID_WEIGHT)
        if (state.tiers.containsKey(tierId)) {
            return MutationResult(success = false, error = Error.TIER_ALREADY_EXISTS)
        }
        state.tiers[tierId] = TierEntry(displayName = displayName, weight = weight)
        return MutationResult(success = true)
    }

    fun renameTier(
        state: TierState,
        oldTierId: String,
        newTierId: String,
    ): MutationResult {
        if (newTierId.isBlank()) return MutationResult(success = false, error = Error.INVALID_TIER_ID)
        val existing = state.tiers[oldTierId] ?: return MutationResult(success = false, error = Error.UNKNOWN_TIER)
        if (state.tiers.containsKey(newTierId)) {
            return MutationResult(success = false, error = Error.TIER_ALREADY_EXISTS)
        }

        state.tiers.remove(oldTierId)
        state.tiers[newTierId] = existing.copy(displayName = existing.displayName.ifBlank { newTierId })

        state.rewardTierAssignments.entries.forEach { (rewardId, assignedTier) ->
            if (assignedTier == oldTierId) {
                state.rewardTierAssignments[rewardId] = newTierId
            }
        }
        return MutationResult(success = true)
    }

    fun deleteTier(
        state: TierState,
        tierId: String,
    ): MutationResult {
        val rewardsUsingTier = rewardsUsingTier(state, tierId)
        if (rewardsUsingTier.isNotEmpty()) {
            return MutationResult(success = false, error = Error.TIER_REFERENCED, referencedRewards = rewardsUsingTier)
        }
        state.tiers.remove(tierId)
        return MutationResult(success = true)
    }

    fun setTierWeight(
        state: TierState,
        tierId: String,
        weight: Int,
    ): MutationResult {
        if (weight < 1) return MutationResult(success = false, error = Error.INVALID_WEIGHT)
        val entry = state.tiers[tierId] ?: return MutationResult(success = false, error = Error.UNKNOWN_TIER)
        entry.weight = weight
        return MutationResult(success = true)
    }

    fun setTierName(
        state: TierState,
        tierId: String,
        displayName: String,
    ): MutationResult {
        val entry = state.tiers[tierId] ?: return MutationResult(success = false, error = Error.UNKNOWN_TIER)
        entry.displayName = displayName
        return MutationResult(success = true)
    }

    fun rewardsUsingTier(
        state: TierState,
        tierId: String,
    ): List<String> =
        state.rewardTierAssignments
            .filterValues { it == tierId }
            .keys
            .sorted()

    fun writeToYaml(
        state: TierState,
        yaml: YamlConfiguration,
    ) {
        yaml.set("tiers", null)
        state.tiers
            .toSortedMap()
            .forEach { (tierId, tier) ->
                yaml.set("tiers.$tierId.name", tier.displayName)
                yaml.set("tiers.$tierId.weight", tier.weight)
            }

        val prizeKeys = yaml.getConfigurationSection("prizes")?.getKeys(false).orEmpty()
        prizeKeys.forEach { rewardId ->
            yaml.set("prizes.$rewardId.tier", state.rewardTierAssignments[rewardId])
        }
    }
}
