package so.alaz.slate.display

import java.util.UUID
import org.bukkit.Location

class DisplayGroup internal constructor(
    val id: UUID,
    val ownerType: String,
    val ownerId: String,
    baseLocation: Location,
    private val removeHandler: (UUID) -> Unit,
    private val teleportHandler: (UUID, Location) -> Unit,
    private val textUpdateHandler: (UUID, List<String>) -> Unit,
) {
    @Volatile
    var baseLocation: Location = baseLocation.clone()
        private set

    @Volatile
    private var entityIds: List<UUID> = emptyList()

    val entityUuids: List<UUID>
        get() = entityIds

    fun remove() {
        removeHandler(id)
    }

    fun teleport(location: Location) {
        baseLocation = location.clone()
        teleportHandler(id, location.clone())
    }

    fun updateLines(lines: List<String>) {
        textUpdateHandler(id, lines)
    }

    internal fun setEntityUuids(ids: List<UUID>) {
        entityIds = ids
    }
}
