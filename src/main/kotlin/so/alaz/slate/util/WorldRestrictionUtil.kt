package so.alaz.slate.util

import org.bukkit.World

object WorldRestrictionUtil {
    fun isAllowed(world: World, allowedWorlds: Set<String>): Boolean =
        allowedWorlds.isEmpty() || allowedWorlds.any { it in identifiers(world) }

    fun identifiers(world: World): Set<String> {
        val values = linkedSetOf(
            world.name,
            world.key.asString(),
            world.key.key,
        )
        values += when (world.environment) {
            World.Environment.NORMAL -> setOf("world", "overworld", "minecraft:world", "minecraft:overworld")
            World.Environment.NETHER -> setOf("world_nether", "the_nether", "nether", "minecraft:world_nether", "minecraft:the_nether", "minecraft:nether")
            World.Environment.THE_END -> setOf("world_the_end", "the_end", "end", "minecraft:world_the_end", "minecraft:the_end", "minecraft:end")
            else -> emptySet()
        }
        return values.filterTo(linkedSetOf()) { it.isNotBlank() }
    }
}
