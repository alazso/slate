package so.alaz.slate.placeholder

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.key.KeyType
import so.alaz.slate.probability.ProbabilityVariableService

class PlaceholderResolverService(
    private val cacheTtl: Duration = Duration.ofSeconds(3),
) {
    private val cache = ConcurrentHashMap<String, CachedValue>()

    fun resolve(player: Player?, params: String): String? {
        val request = PlaceholderRequestParser.parse(params) ?: return null
        return runCatching { resolveRequest(player, request) }
            .getOrNull()
    }

    private fun resolveRequest(player: Player?, request: PlaceholderRequest): String? = when (request.key) {
        "keys" -> withPlayerCrate(player, request.args) { p, crate ->
            val virtualKeys = blocking { SlateAPI.keyService()?.getVirtualKeys(p.uniqueId, crate.id) ?: 0 }
            val physicalKeys = blocking { SlateAPI.keyService()?.countKeys(p, crate, KeyType.Physical) ?: 0 }
            (virtualKeys + physicalKeys).toString()
        }

        "virtual_keys" -> withPlayerCrate(player, request.args) { p, crate ->
            blocking { SlateAPI.keyService()?.getVirtualKeys(p.uniqueId, crate.id) ?: 0 }.toString()
        }

        "physical_keys" -> withPlayerCrate(player, request.args) { p, crate ->
            blocking { SlateAPI.keyService()?.countKeys(p, crate, KeyType.Physical) ?: 0 }.toString()
        }

        "pity" -> withPlayerCrate(player, request.args) { p, crate ->
            blocking { SlateAPI.storageService()?.getPityCount(p.uniqueId, crate.id) ?: 0 }.toString()
        }

        "cooldown", "cooldown_remaining" -> withPlayerCrate(player, request.args) { p, crate ->
            cooldownRemaining(p.uniqueId, crate)
        }

        "cooldown_active" -> withPlayerCrate(player, request.args) { p, crate ->
            val remaining = cooldownRemainingDuration(p.uniqueId, crate)
            if (remaining.isZero || remaining.isNegative) "false" else "true"
        }

        "opens" -> withPlayerCrate(player, request.args) { p, crate ->
            blocking { SlateAPI.storageService()?.countOpens(p.uniqueId, crate.id) ?: 0 }.toString()
        }

        "opens_remaining" -> withPlayerCrate(player, request.args) { p, crate ->
            if (crate.maxPulls < 0) {
                "unlimited"
            } else {
                val used = blocking { SlateAPI.storageService()?.countOpens(p.uniqueId, crate.id) ?: 0 }
                (crate.maxPulls - used).coerceAtLeast(0).toString()
            }
        }

        "total_opened" -> withPlayer(player) { p ->
            blocking { SlateAPI.storageService()?.countTotalOpens(p.uniqueId) ?: 0 }.toString()
        }

        "eligibility_open" -> withPlayerCrate(player, request.args) { p, crate ->
            SlateAPI.crateEligibilityService()?.canOpen(p, crate)?.allowed?.toString() ?: "false"
        }

        "eligibility_preview" -> withPlayerCrate(player, request.args) { p, crate ->
            SlateAPI.crateEligibilityService()?.canPreview(p, crate)?.allowed?.toString() ?: "false"
        }

        "crate_name" -> withCrateArg(request.args) { crate -> crate.name }
        "crate_state" -> withCrateArg(request.args) { crate -> crate.lifecycle.state.name.lowercase() }
        "crate_required_keys" -> withCrateArg(request.args) { crate -> crate.requiredKeys.toString() }
        "crate_mode" -> withCrateArg(request.args) { crate -> crate.selection.mode.name.lowercase() }
        "crate_structure" -> withCrateArg(request.args) { crate -> crate.selection.structure.name.lowercase() }
        "crate_tiers" -> withCrateArg(request.args) { crate -> crate.tiers.size.toString() }
        "crate_prizes" -> withCrateArg(request.args) { crate -> crate.prizes.size.toString() }

        "top_reward", "top_chance", "rarest_reward", "rarest_chance" -> withCrateArg(request.args) { crate ->
            ProbabilityVariableService.crateVariables(crate.id)[request.key] ?: "N/A"
        }

        "reward_chance", "reward_effective_chance", "reward_tier" -> {
            if (request.args.size < 2) return null
            val crate = crateById(request.args[0]) ?: return "N/A"
            val rewardId = request.args[1]
            val variables = ProbabilityVariableService.rewardVariables(crate.id, rewardId)
            when (request.key) {
                "reward_tier" -> variables["tier"] ?: "N/A"
                "reward_effective_chance" -> variables["effective_chance"] ?: "N/A"
                else -> variables["chance"] ?: "N/A"
            }
        }

        "latest_open" -> {
            if (request.args.size < 2) return null
            val crate = crateById(request.args[0]) ?: return "N/A"
            val uuid = runCatching { UUID.fromString(request.args[1]) }.getOrNull() ?: return "N/A"
            blocking { SlateAPI.storageService()?.getLatestOpen(uuid, crate.id) }
                ?.prizeName
                ?: "N/A"
        }

        "last_winner" -> withCachedCrate("last_winner", request.args) { crate ->
            val latest = blocking { SlateAPI.storageService()?.getLatestOpen(crate.id) } ?: return@withCachedCrate "N/A"
            playerName(latest.uuid)
        }

        "last_prize" -> withCachedCrate("last_prize", request.args) { crate ->
            blocking { SlateAPI.storageService()?.getLatestOpen(crate.id)?.prizeName ?: "N/A" }
        }

        "crate_open_total" -> withCachedCrate("crate_open_total", request.args) { crate ->
            blocking { SlateAPI.storageService()?.countTotalOpens(crate.id) ?: 0 }.toString()
        }

        "pending_count" -> {
            val target = when {
                request.args.isNotEmpty() -> Bukkit.getPlayerExact(request.args.first())
                else -> player
            } ?: return "0"
            blocking { SlateAPI.storageService()?.getPendingPrizes(target.uniqueId)?.size ?: 0 }.toString()
        }

        "anchor_count" -> {
            val anchors = blocking { SlateAPI.anchorService()?.allAnchors().orEmpty() }
            if (request.args.isEmpty()) {
                anchors.size.toString()
            } else {
                val crateId = request.args.first()
                anchors.count { it.crateId.equals(crateId, ignoreCase = true) }.toString()
            }
        }

        "recent_operation_state" -> withCached("recent_operation_state") {
            blocking {
                SlateAPI.storageService()
                    ?.listRecentOpenOperations(1)
                    ?.firstOrNull()
                    ?.state
                    ?.name
                    ?.lowercase()
            } ?: "none"
        }

        else -> null
    }

    private fun withPlayer(player: Player?, block: (Player) -> String): String =
        player?.let(block) ?: "N/A"

    private fun withPlayerCrate(player: Player?, args: List<String>, block: (Player, CrateDefinition) -> String): String {
        val p = player ?: return "N/A"
        val crate = crateByArg(args) ?: return "N/A"
        return block(p, crate)
    }

    private fun withCrateArg(args: List<String>, block: (CrateDefinition) -> String): String {
        val crate = crateByArg(args) ?: return "N/A"
        return block(crate)
    }

    private fun withCachedCrate(prefix: String, args: List<String>, block: (CrateDefinition) -> String): String {
        val crate = crateByArg(args) ?: return "N/A"
        return withCached("$prefix:${crate.id}") { block(crate) }
    }

    private fun withCached(key: String, block: () -> String): String {
        val now = System.currentTimeMillis()
        val cached = cache[key]
        if (cached != null && now - cached.createdAt <= cacheTtl.toMillis()) {
            return cached.value
        }
        val value = block()
        cache[key] = CachedValue(value = value, createdAt = now)
        return value
    }

    private fun crateByArg(args: List<String>): CrateDefinition? =
        args.firstOrNull()?.let(::crateById)

    private fun crateById(crateId: String): CrateDefinition? =
        SlateAPI.crateManager()?.byId(crateId)

    private fun playerName(uuid: UUID): String =
        Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()

    private fun cooldownRemaining(uuid: UUID, crate: CrateDefinition): String {
        val remaining = cooldownRemainingDuration(uuid, crate)
        if (remaining.isZero || remaining.isNegative) return "ready"
        return formatDuration(remaining)
    }

    private fun cooldownRemainingDuration(uuid: UUID, crate: CrateDefinition): Duration {
        val lastOpen = blocking { SlateAPI.storageService()?.getCooldown(uuid, crate.id) } ?: return Duration.ZERO
        val cooldown = Duration.ofMillis(crate.cooldown.inWholeMilliseconds)
        if (cooldown.isZero || cooldown.isNegative) return Duration.ZERO
        val readyAt = lastOpen.plus(cooldown)
        return Duration.between(Instant.now(), readyAt)
    }

    private fun formatDuration(duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0)
        val minutes = seconds / 60
        val remSeconds = seconds % 60
        return if (minutes > 0) {
            "${minutes}m ${remSeconds}s"
        } else {
            "${remSeconds}s"
        }
    }

    private fun <T> blocking(block: suspend () -> T): T =
        runBlocking { block() }

    private data class CachedValue(
        val value: String,
        val createdAt: Long,
    )
}
