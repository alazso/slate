package so.alaz.slate.crate

import org.bukkit.entity.Player

interface RegionAccessService {
    val available: Boolean

    fun regionsAt(player: Player): Set<String>?
}

object NoopRegionAccessService : RegionAccessService {
    override val available: Boolean = false

    override fun regionsAt(player: Player): Set<String>? = null
}

class WorldGuardRegionAccessService : RegionAccessService {
    override val available: Boolean by lazy {
        val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WorldGuard")
        plugin?.isEnabled == true
    }

    override fun regionsAt(player: Player): Set<String>? {
        if (!available) return null
        return runCatching {
            val worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard")
            val wgInstance = worldGuardClass.getMethod("getInstance").invoke(null)
            val platform = wgInstance.javaClass.getMethod("getPlatform").invoke(wgInstance)
            val container = platform.javaClass.getMethod("getRegionContainer").invoke(platform)

            val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
            val weWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World::class.java).invoke(null, player.world)
            val getMethod = container.javaClass.methods.firstOrNull {
                it.name == "get" && it.parameterCount == 1
            } ?: return@runCatching null
            val regionManager = getMethod.invoke(container, weWorld)
                ?: return@runCatching emptySet<String>()

            val blockVectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3")
            val atMethod = blockVectorClass.getMethod("at", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val blockVector = atMethod.invoke(null, player.location.blockX, player.location.blockY, player.location.blockZ)

            val applicable = regionManager.javaClass.getMethod("getApplicableRegions", blockVectorClass).invoke(regionManager, blockVector)
            val iterator = applicable.javaClass.getMethod("iterator").invoke(applicable) as Iterator<*>
            buildSet {
                while (iterator.hasNext()) {
                    val region = iterator.next() ?: continue
                    val id = region.javaClass.getMethod("getId").invoke(region)?.toString()?.trim().orEmpty()
                    if (id.isNotBlank()) add(id.lowercase())
                }
            }
        }.getOrNull()
    }
}

