package so.alaz.slate.command.sub

import java.util.LinkedHashMap
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure

internal object RewardMutationSupport {
    data class RewardEntry(
        var id: String,
        var name: String,
        var tier: String?,
        var weight: Int,
        var chance: Double?,
        var broadcast: Boolean,
        var broadcastMessage: String,
        val items: MutableList<PrizeItem>,
        val commands: MutableList<String>,
        val messages: MutableList<String>,
    )

    data class RewardState(
        val rewards: LinkedHashMap<String, RewardEntry>,
    )

    enum class Error {
        UNKNOWN_REWARD,
        REWARD_ALREADY_EXISTS,
        INVALID_REWARD_ID,
        INVALID_WEIGHT,
        INVALID_CHANCE,
        INVALID_MODE,
        INVALID_TIER,
        ITEMS_REQUIRED,
    }

    data class MutationResult(
        val success: Boolean,
        val error: Error? = null,
    )

    fun fromCrate(crate: CrateDefinition): RewardState =
        RewardState(
            rewards = LinkedHashMap(
                crate.prizes
                    .sortedBy { it.id }
                    .associate { prize -> prize.id to prize.toEntry() },
            ),
        )

    fun fromYaml(yaml: YamlConfiguration): RewardState {
        val rewards = LinkedHashMap<String, RewardEntry>()
        yaml.getConfigurationSection("prizes")
            ?.getKeys(false)
            ?.sorted()
            ?.forEach { rewardId ->
                rewards[rewardId] = RewardEntry(
                    id = rewardId,
                    name = yaml.getString("prizes.$rewardId.name", rewardId).orEmpty(),
                    tier = yaml.getString("prizes.$rewardId.tier"),
                    weight = yaml.getInt("prizes.$rewardId.weight", 0),
                    chance = (yaml.get("prizes.$rewardId.chance") as? Number)?.toDouble(),
                    broadcast = yaml.getBoolean("prizes.$rewardId.broadcast", false),
                    broadcastMessage = yaml.getString("prizes.$rewardId.broadcastMessage", "").orEmpty(),
                    items = parseItems(yaml.getList("prizes.$rewardId.items")).toMutableList(),
                    commands = yaml.getStringList("prizes.$rewardId.commands").toMutableList(),
                    messages = yaml.getStringList("prizes.$rewardId.messages").toMutableList(),
                )
            }
        return RewardState(rewards = rewards)
    }

    fun createReward(
        state: RewardState,
        crate: CrateDefinition,
        rewardId: String,
    ): MutationResult {
        if (rewardId.isBlank()) return MutationResult(false, Error.INVALID_REWARD_ID)
        if (state.rewards.containsKey(rewardId)) return MutationResult(false, Error.REWARD_ALREADY_EXISTS)
        state.rewards[rewardId] = RewardEntry(
            id = rewardId,
            name = rewardId,
            tier = crate.tiers.keys.firstOrNull().takeIf { crate.selection.structure == SelectionStructure.TIERS },
            weight = if (crate.selection.mode == SelectionMode.WEIGHTED) 1 else 0,
            chance = if (crate.selection.mode == SelectionMode.PERCENT) 1.0 else null,
            broadcast = false,
            broadcastMessage = "",
            items = mutableListOf(PrizeItem(material = "CHEST")),
            commands = mutableListOf(),
            messages = mutableListOf("<gray>You received $rewardId</gray>"),
        )
        return MutationResult(true)
    }

    fun deleteReward(state: RewardState, rewardId: String): MutationResult {
        if (!state.rewards.containsKey(rewardId)) return MutationResult(false, Error.UNKNOWN_REWARD)
        state.rewards.remove(rewardId)
        return MutationResult(true)
    }

    fun renameReward(
        state: RewardState,
        oldRewardId: String,
        newRewardId: String,
    ): MutationResult {
        if (newRewardId.isBlank()) return MutationResult(false, Error.INVALID_REWARD_ID)
        val entry = state.rewards[oldRewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        if (state.rewards.containsKey(newRewardId)) return MutationResult(false, Error.REWARD_ALREADY_EXISTS)
        state.rewards.remove(oldRewardId)
        entry.id = newRewardId
        state.rewards[newRewardId] = entry
        return MutationResult(true)
    }

    fun setRewardName(
        state: RewardState,
        rewardId: String,
        value: String,
    ): MutationResult {
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.name = value
        return MutationResult(true)
    }

    fun setRewardTier(
        state: RewardState,
        crate: CrateDefinition,
        rewardId: String,
        tier: String?,
    ): MutationResult {
        if (crate.selection.structure != SelectionStructure.TIERS) return MutationResult(false, Error.INVALID_MODE)
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        val tierId = tier?.trim().orEmpty()
        if (!crate.tiers.containsKey(tierId)) return MutationResult(false, Error.INVALID_TIER)
        entry.tier = tierId
        return MutationResult(true)
    }

    fun setRewardWeight(
        state: RewardState,
        crate: CrateDefinition,
        rewardId: String,
        weight: Int,
    ): MutationResult {
        if (crate.selection.mode != SelectionMode.WEIGHTED) return MutationResult(false, Error.INVALID_MODE)
        if (weight <= 0) return MutationResult(false, Error.INVALID_WEIGHT)
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.weight = weight
        entry.chance = null
        return MutationResult(true)
    }

    fun setRewardChance(
        state: RewardState,
        crate: CrateDefinition,
        rewardId: String,
        chance: Double,
    ): MutationResult {
        if (crate.selection.mode != SelectionMode.PERCENT || crate.selection.structure != SelectionStructure.FLAT) {
            return MutationResult(false, Error.INVALID_MODE)
        }
        if (chance <= 0.0 || chance > 100.0) return MutationResult(false, Error.INVALID_CHANCE)
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.chance = chance
        entry.weight = 0
        return MutationResult(true)
    }

    fun setBroadcast(
        state: RewardState,
        rewardId: String,
        enabled: Boolean,
    ): MutationResult {
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.broadcast = enabled
        return MutationResult(true)
    }

    fun setBroadcastMessage(
        state: RewardState,
        rewardId: String,
        message: String,
    ): MutationResult {
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.broadcastMessage = message
        return MutationResult(true)
    }

    fun setItems(
        state: RewardState,
        rewardId: String,
        items: List<PrizeItem>,
    ): MutationResult {
        if (items.isEmpty()) return MutationResult(false, Error.ITEMS_REQUIRED)
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.items.clear()
        entry.items.addAll(items)
        return MutationResult(true)
    }

    fun setCommands(
        state: RewardState,
        rewardId: String,
        commands: List<String>,
    ): MutationResult {
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.commands.clear()
        entry.commands.addAll(commands.filter { it.isNotBlank() })
        return MutationResult(true)
    }

    fun setMessages(
        state: RewardState,
        rewardId: String,
        messages: List<String>,
    ): MutationResult {
        val entry = state.rewards[rewardId] ?: return MutationResult(false, Error.UNKNOWN_REWARD)
        entry.messages.clear()
        entry.messages.addAll(messages.filter { it.isNotBlank() })
        return MutationResult(true)
    }

    fun writeToYaml(state: RewardState, yaml: YamlConfiguration) {
        yaml.set("prizes", null)
        state.rewards.values.sortedBy { it.id }.forEach { entry ->
            val path = "prizes.${entry.id}"
            yaml.set("$path.name", entry.name)
            yaml.set("$path.tier", entry.tier)
            yaml.set("$path.weight", entry.weight)
            yaml.set("$path.chance", entry.chance)
            yaml.set("$path.broadcast", entry.broadcast)
            yaml.set("$path.broadcastMessage", entry.broadcastMessage)
            yaml.set("$path.items", entry.items.map { serializePrizeItem(it) })
            yaml.set("$path.commands", entry.commands)
            yaml.set("$path.messages", entry.messages)
        }
    }

    private fun Prize.toEntry(): RewardEntry =
        RewardEntry(
            id = id,
            name = name,
            tier = tier,
            weight = weight,
            chance = chance,
            broadcast = broadcast,
            broadcastMessage = broadcastMessage,
            items = items.toMutableList(),
            commands = commands.toMutableList(),
            messages = messages.toMutableList(),
        )

    private fun parseItems(raw: List<*>?): List<PrizeItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.mapNotNull { entry ->
            when (entry) {
                is String -> PrizeItem(material = entry, amount = 1)
                is Map<*, *> -> {
                    val material = (entry["material"] as? String).orEmpty()
                    val amount = (entry["amount"] as? Number)?.toInt() ?: 1
                    val adapter = entry["adapter"] as? String
                    val itemId = entry["itemId"] as? String
                    val serialized = entry["serialized"] as? String
                    val customModelData = (entry["customModelData"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }.orEmpty()
                    val pdcRaw = entry["pdc"] as? Map<*, *>
                    val pdc = pdcRaw?.entries
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v?.toString() ?: return@mapNotNull null
                            key to value
                        }?.toMap().orEmpty()
                    PrizeItem(
                        material = material,
                        amount = amount,
                        adapter = adapter,
                        itemId = itemId,
                        serialized = serialized,
                        customModelData = customModelData,
                        pdc = pdc,
                    )
                }
                else -> null
            }
        }
    }

    private fun serializePrizeItem(item: PrizeItem): Any {
        if (
            item.adapter == null &&
            item.itemId == null &&
            item.serialized == null &&
            item.customModelData.isEmpty() &&
            item.pdc.isEmpty() &&
            item.amount == 1
        ) {
            return item.material
        }
        return linkedMapOf<String, Any?>(
            "material" to item.material,
            "amount" to item.amount,
            "adapter" to item.adapter,
            "itemId" to item.itemId,
            "serialized" to item.serialized,
            "customModelData" to item.customModelData.takeIf { it.isNotEmpty() },
            "pdc" to item.pdc.takeIf { it.isNotEmpty() },
        ).filterValues { it != null }
    }
}
