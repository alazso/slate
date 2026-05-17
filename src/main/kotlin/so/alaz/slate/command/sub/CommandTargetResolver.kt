package so.alaz.slate.command.sub

import java.util.UUID
import org.bukkit.Bukkit

object CommandTargetResolver {
    fun resolveUuid(input: String): UUID? {
        runCatching { UUID.fromString(input) }.getOrNull()?.let { return it }
        Bukkit.getPlayerExact(input)?.uniqueId?.let { return it }
        Bukkit.getOfflinePlayerIfCached(input)?.uniqueId?.let { return it }
        return null
    }
}
