package so.alaz.slate.display

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import so.alaz.slate.SlateKeys
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.util.ComponentUtil
import so.alaz.slate.util.LogUtil

class DisplayService(
    private val scheduler: PlatformScheduler,
    private val crateManager: CrateManager,
    private val anchorService: AnchorService,
    private val factory: DisplayFactory,
    private val pluginScope: CoroutineScope,
) {
    private val groups = ConcurrentHashMap<UUID, DisplayGroup>()
    private val displayEntitiesAvailable = displayClassAvailable("org.bukkit.entity.TextDisplay") &&
        displayClassAvailable("org.bukkit.entity.ItemDisplay")

    fun createDisplayGroup(spec: DisplayGroupSpec): DisplayGroup? {
        if (!displayEntitiesAvailable) {
            LogUtil.warn("Display entities are unavailable on this server; crate displays are disabled.")
            return null
        }
        if (spec.baseLocation.world == null) {
            LogUtil.warn("Skipping display for ${spec.ownerType}:${spec.ownerId}; world is not loaded.")
            return null
        }

        val group = DisplayGroup(
            id = UUID.randomUUID(),
            ownerType = spec.ownerType,
            ownerId = spec.ownerId,
            baseLocation = spec.baseLocation,
            removeHandler = ::removeDisplayGroup,
            teleportHandler = ::teleportDisplayGroup,
            textUpdateHandler = ::updateDisplayGroupLines,
        )
        groups[group.id] = group

        scheduler.runAtLocation(spec.baseLocation) {
            runCatching {
                val world = spec.baseLocation.world
                if (world == null) {
                    groups.remove(group.id)
                    LogUtil.warn("Skipping display for ${spec.ownerType}:${spec.ownerId}; world is not loaded.")
                    return@runCatching
                }
                if (!world.isChunkLoaded(spec.baseLocation.blockX shr 4, spec.baseLocation.blockZ shr 4)) {
                    groups.remove(group.id)
                    LogUtil.debug("Skipping display for ${spec.ownerType}:${spec.ownerId}; chunk is not loaded.")
                    return@runCatching
                }
                val spawned = spec.elements.mapNotNull { spawnElement(group, spec.baseLocation, it) }
                group.setEntityUuids(spawned.map { it.uniqueId })
            }.onFailure { error ->
                groups.remove(group.id)
                LogUtil.warn("Failed to spawn display for ${spec.ownerType}:${spec.ownerId}: ${error.message}")
            }
        }
        return group
    }

    fun updateDisplayGroup(spec: DisplayGroupSpec): DisplayGroup? {
        removeDisplayGroups(spec.ownerType, spec.ownerId)
        return createDisplayGroup(spec)
    }

    fun removeDisplayGroup(groupId: UUID) {
        val group = groups.remove(groupId) ?: return
        scheduler.runAtLocation(group.baseLocation) {
            group.entityUuids.forEach { entityId ->
                val entity = Bukkit.getEntity(entityId)
                if (entity != null && isOwnedDisplay(entity)) {
                    entity.remove()
                }
            }
            removeNearbyOwnedDisplays(group.baseLocation, 4.0)
        }
    }

    fun removeDisplayGroups(ownerType: String, ownerId: String) {
        groups.values
            .filter { it.ownerType == ownerType && it.ownerId == ownerId }
            .map { it.id }
            .forEach(::removeDisplayGroup)
    }

    fun removeDisplayGroupAt(ownerType: String, ownerId: String, location: Location) {
        groups.values
            .filter { it.ownerType == ownerType && it.ownerId == ownerId && it.baseLocation.sameBlock(location) }
            .map { it.id }
            .forEach(::removeDisplayGroup)
        cleanupStaleDisplaysNear(location, 4.0, ownerType, ownerId)
    }

    fun removeAllOwnedDisplays() {
        groups.keys.toList().forEach(::removeDisplayGroup)
        cleanupAllStaleDisplays()
    }

    fun reloadDisplays() {
        removeAllOwnedDisplays()
        pluginScope.launch {
            val anchors = anchorService.allAnchors()
            scheduler.runGlobal {
                anchors.forEach { anchor ->
                    val crate = crateManager.byId(anchor.crateId) ?: return@forEach
                    val worldKey = NamespacedKey.fromString(anchor.worldKey)
                    val world = worldKey?.let(Bukkit::getWorld) ?: return@forEach
                    val base = Location(world, anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5)
                    val spec = factory.crateDisplay(crate, base) ?: return@forEach
                    createDisplayGroup(spec)
                }
            }
        }
    }

    fun cleanupStaleDisplaysNear(
        location: Location,
        radius: Double,
        ownerType: String? = null,
        ownerId: String? = null,
    ) {
        if (location.world == null) return
        scheduler.runAtLocation(location) {
            removeNearbyOwnedDisplays(location, radius, ownerType, ownerId)
        }
    }

    fun cleanupAllStaleDisplays() {
        scheduler.runGlobal {
            Bukkit.getWorlds().forEach { world ->
                world.loadedChunks.forEach { chunk ->
                    val location = Location(world, chunk.x * 16.0 + 8.0, world.minHeight.toDouble(), chunk.z * 16.0 + 8.0)
                    scheduler.runAtLocation(location) {
                        chunk.entities
                            .filter { isOwnedDisplay(it) }
                            .forEach { it.remove() }
                    }
                }
            }
        }
    }

    private fun teleportDisplayGroup(groupId: UUID, location: Location) {
        val group = groups[groupId] ?: return
        scheduler.runAtLocation(group.baseLocation) {
            group.entityUuids.forEach { entityId ->
                val entity = Bukkit.getEntity(entityId) ?: return@forEach
                if (isOwnedDisplay(entity)) {
                    entity.teleport(location)
                }
            }
        }
    }

    private fun updateDisplayGroupLines(groupId: UUID, lines: List<String>) {
        val group = groups[groupId] ?: return
        scheduler.runAtLocation(group.baseLocation) {
            var lineIndex = 0
            group.entityUuids.forEach { entityId ->
                val entity = Bukkit.getEntity(entityId) ?: return@forEach
                if (entity is TextDisplay && isOwnedDisplay(entity) && lineIndex < lines.size) {
                    entity.text(ComponentUtil.parse(lines[lineIndex]))
                    lineIndex++
                }
            }
        }
    }

    private fun spawnElement(group: DisplayGroup, baseLocation: Location, spec: DisplayElementSpec): Entity? {
        val world = baseLocation.world ?: return null
        val location = baseLocation.clone().add(spec.offset)
        return when (spec.type) {
            DisplayElementType.TEXT -> world.spawn(location, TextDisplay::class.java) { entity ->
                configureDisplay(entity, group, spec, "text")
                entity.text(spec.text?.let(ComponentUtil::parse) ?: Component.empty())
                entity.alignment = spec.alignment
                entity.isShadowed = spec.shadowed
                entity.isSeeThrough = spec.seeThrough
                spec.backgroundColor?.let { entity.backgroundColor = it }
            }

            DisplayElementType.ITEM -> world.spawn(location, ItemDisplay::class.java) { entity ->
                configureDisplay(entity, group, spec, "item")
                spec.itemStack?.let { entity.setItemStack(it.clone()) }
                entity.itemDisplayTransform = spec.itemTransform
            }
        }
    }

    private fun configureDisplay(entity: Display, group: DisplayGroup, spec: DisplayElementSpec, elementType: String) {
        entity.isPersistent = false
        entity.customName(null)
        entity.isCustomNameVisible = false
        entity.billboard = spec.billboard
        entity.viewRange = spec.viewRange
        entity.transformation = Transformation(
            Vector3f(),
            Quaternionf(),
            Vector3f(spec.scale, spec.scale, spec.scale),
            Quaternionf(),
        )
        entity.persistentDataContainer.set(SlateKeys.DISPLAY_OWNED, PersistentDataType.BYTE, 1)
        entity.persistentDataContainer.set(SlateKeys.DISPLAY_GROUP_ID, PersistentDataType.STRING, group.id.toString())
        entity.persistentDataContainer.set(SlateKeys.DISPLAY_OWNER_TYPE, PersistentDataType.STRING, group.ownerType)
        entity.persistentDataContainer.set(SlateKeys.DISPLAY_OWNER_ID, PersistentDataType.STRING, group.ownerId)
        entity.persistentDataContainer.set(SlateKeys.DISPLAY_ELEMENT_TYPE, PersistentDataType.STRING, elementType)
        entity.addScoreboardTag("slate_display")
        entity.addScoreboardTag("slate_${group.ownerType}_display")
    }

    private fun isOwnedDisplay(entity: Entity, ownerType: String? = null, ownerId: String? = null): Boolean {
        val container = entity.persistentDataContainer
        val owned = container.has(SlateKeys.DISPLAY_OWNED, PersistentDataType.BYTE) ||
            entity.scoreboardTags.contains("slate_display")
        if (!owned) return false
        if (ownerType != null && container.get(SlateKeys.DISPLAY_OWNER_TYPE, PersistentDataType.STRING) != ownerType) return false
        if (ownerId != null && container.get(SlateKeys.DISPLAY_OWNER_ID, PersistentDataType.STRING) != ownerId) return false
        return true
    }

    private fun removeNearbyOwnedDisplays(
        location: Location,
        radius: Double,
        ownerType: String? = null,
        ownerId: String? = null,
    ) {
        val world = location.world ?: return
        world.getNearbyEntities(location, radius, radius, radius)
            .filter { isOwnedDisplay(it, ownerType, ownerId) }
            .forEach { it.remove() }
    }

    private fun Location.sameBlock(other: Location): Boolean =
        world?.uid == other.world?.uid &&
            blockX == other.blockX &&
            blockY == other.blockY &&
            blockZ == other.blockZ

    private fun displayClassAvailable(className: String): Boolean = runCatching {
        Class.forName(className)
    }.isSuccess
}
