package so.alaz.slate.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import so.alaz.slate.SlatePlugin

class SlatePlaceholderExpansion(
    private val plugin: SlatePlugin,
    private val resolver: PlaceholderResolverService = PlaceholderResolverService(),
) : PlaceholderExpansion() {
    constructor(plugin: SlatePlugin) : this(plugin, PlaceholderResolverService())

    override fun getIdentifier(): String = "slate"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val onlinePlayer = player?.player
        return resolver.resolve(onlinePlayer, params)
    }
}
