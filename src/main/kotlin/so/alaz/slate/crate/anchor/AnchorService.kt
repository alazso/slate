package so.alaz.slate.crate.anchor

import org.bukkit.Location
import so.alaz.slate.storage.StorageService
import java.util.concurrent.ConcurrentHashMap

class AnchorService(
    private val storageService: StorageService,
) {
    private val anchorCache = ConcurrentHashMap<AnchorKey, String>()

    suspend fun warmCache() {
        val anchors = storageService.listCrateAnchors()
        anchorCache.clear()
        anchors.forEach { anchor ->
            anchorCache[AnchorKey(anchor.worldKey, anchor.x, anchor.y, anchor.z)] = anchor.crateId
        }
    }

    suspend fun addOrReplaceAnchor(crateId: String, location: Location) {
        val world = requireNotNull(location.world) { "Anchor location world is null" }
        storageService.upsertCrateAnchor(
            crateId = crateId,
            worldKey = world.key.asString(),
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )
        anchorCache[AnchorKey(world.key.asString(), location.blockX, location.blockY, location.blockZ)] = crateId
    }

    suspend fun removeAnchor(location: Location): Boolean {
        val world = requireNotNull(location.world) { "Anchor location world is null" }
        val removed = storageService.removeCrateAnchor(
            worldKey = world.key.asString(),
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )
        if (removed) {
            anchorCache.remove(AnchorKey(world.key.asString(), location.blockX, location.blockY, location.blockZ))
        }
        return removed
    }

    fun crateIdAtCached(location: Location): String? {
        val world = requireNotNull(location.world) { "Anchor location world is null" }
        return anchorCache[AnchorKey(world.key.asString(), location.blockX, location.blockY, location.blockZ)]
    }

    suspend fun crateIdAt(location: Location): String? {
        val world = requireNotNull(location.world) { "Anchor location world is null" }
        return storageService.getCrateAnchor(
            worldKey = world.key.asString(),
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )?.crateId
    }

    suspend fun allAnchors(): List<CrateAnchor> = storageService.listCrateAnchors()

    private data class AnchorKey(
        val worldKey: String,
        val x: Int,
        val y: Int,
        val z: Int,
    )
}
