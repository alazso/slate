package so.alaz.slate.player

import java.time.Instant
import org.bukkit.entity.Player
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.key.KeyService
import so.alaz.slate.storage.OpenHistoryEntry
import so.alaz.slate.storage.PendingPrize
import so.alaz.slate.storage.StorageService

/**
 * Read-only facade exposing per-player crate data to GUI screens and commands.
 *
 * All methods are suspend and safe to call from coroutine scopes; they delegate to storage
 * and never mutate state. By centralising the data contract here, the GUIs become pure
 * view layers.
 */
class PlayerCrateQueryService(
    private val storage: StorageService,
    private val crateManager: CrateManager,
    private val keyService: KeyService,
    private val anchorService: AnchorService,
    private val claimService: ClaimService,
) {
    /** Snapshot of the player's virtual key holdings across every loaded crate. */
    suspend fun keyWallet(player: Player): KeyWalletSnapshot {
        val crates = crateManager.all().sortedBy { it.id }
        val entries = crates.map { crate ->
            val virtual = keyService.getVirtualKeys(player.uniqueId, crate.id)
            KeyWalletEntry(crate = crate, virtualKeys = virtual)
        }
        return KeyWalletSnapshot(entries = entries, totalVirtualKeys = entries.sumOf { it.virtualKeys })
    }

    /** Per-crate pity progress, restricted to crates with pity enabled. */
    suspend fun pityProgress(player: Player): List<PityProgressEntry> {
        val crates = crateManager.all()
            .filter { it.pityConfig?.enabled == true }
            .sortedBy { it.id }
        val entries = mutableListOf<PityProgressEntry>()
        for (crate in crates) {
            val pity = checkNotNull(crate.pityConfig)
            val current = storage.getPityCount(player.uniqueId, crate.id)
            val remaining = (pity.opensRequired - current).coerceAtLeast(0)
            entries += PityProgressEntry(
                crate = crate,
                current = current,
                required = pity.opensRequired,
                remaining = remaining,
                guaranteedTier = pity.guaranteedTier,
            )
        }
        return entries
    }

    /** Picks the crate that is closest to firing pity — useful for the main menu summary. */
    suspend fun closestPityProgress(player: Player): PityProgressEntry? =
        pityProgress(player)
            .filter { it.required > 0 }
            .minByOrNull { it.remaining }

    /** Per-crate lifetime open totals, sorted newest-first by activity. */
    suspend fun lifetimeOpens(player: Player): Int = storage.countTotalOpens(player.uniqueId)

    suspend fun lifetimeOpens(player: Player, crateId: String): Int =
        storage.countOpens(player.uniqueId, crateId)

    suspend fun latestOpen(player: Player, crateId: String): OpenHistoryEntry? =
        storage.getLatestOpen(player.uniqueId, crateId)

    suspend fun openHistory(player: Player, limit: Int, crateIdFilter: String? = null): List<OpenHistoryEntry> =
        storage.listOpenHistory(player.uniqueId, limit)
            .filter { crateIdFilter.isNullOrBlank() || it.crateId == crateIdFilter }

    suspend fun nearestAnchor(player: Player): AnchorLookup? {
        val anchors = anchorService.allAnchors()
            .filter { it.worldKey == player.world.key.asString() }
        val nearest = anchors.minByOrNull { anchor ->
            val dx = player.location.blockX - anchor.x
            val dy = player.location.blockY - anchor.y
            val dz = player.location.blockZ - anchor.z
            (dx * dx) + (dy * dy) + (dz * dz)
        } ?: return null
        return AnchorLookup(nearest.crateId, nearest.worldKey, nearest.x, nearest.y, nearest.z)
    }

    suspend fun nearestAnchorForCrate(player: Player, crateId: String): AnchorLookup? {
        val anchors = anchorService.allAnchors()
            .filter { it.crateId == crateId && it.worldKey == player.world.key.asString() }
        val nearest = anchors.minByOrNull { anchor ->
            val dx = player.location.blockX - anchor.x
            val dy = player.location.blockY - anchor.y
            val dz = player.location.blockZ - anchor.z
            (dx * dx) + (dy * dy) + (dz * dz)
        } ?: return null
        return AnchorLookup(nearest.crateId, nearest.worldKey, nearest.x, nearest.y, nearest.z)
    }

    suspend fun pendingPrizes(player: Player): List<PendingPrize> =
        storage.getPendingPrizes(player.uniqueId)

    suspend fun pendingSummary(player: Player): PendingSummary {
        val pending = storage.getPendingPrizes(player.uniqueId)
        return PendingSummary(
            count = pending.size,
            oldestCreatedAt = pending.minOfOrNull { it.createdAt },
            newestCreatedAt = pending.maxOfOrNull { it.createdAt },
        )
    }

    suspend fun claimPending(player: Player): ClaimSummary = claimService.claimPending(player)
}

data class KeyWalletSnapshot(
    val entries: List<KeyWalletEntry>,
    val totalVirtualKeys: Int,
) {
    val cratesWithKeys: List<KeyWalletEntry> get() = entries.filter { it.virtualKeys > 0 }
}

data class KeyWalletEntry(
    val crate: CrateDefinition,
    val virtualKeys: Int,
) {
    val opensAvailable: Int
        get() = if (crate.requiredKeys <= 0) virtualKeys else virtualKeys / crate.requiredKeys
}

data class PityProgressEntry(
    val crate: CrateDefinition,
    val current: Int,
    val required: Int,
    val remaining: Int,
    val guaranteedTier: String,
) {
    val isNearGuarantee: Boolean get() = required > 0 && remaining in 0..4
}

data class AnchorLookup(
    val crateId: String,
    val worldKey: String,
    val x: Int,
    val y: Int,
    val z: Int,
)

data class PendingSummary(
    val count: Int,
    val oldestCreatedAt: Instant?,
    val newestCreatedAt: Instant?,
)
