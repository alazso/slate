package so.alaz.slate.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.util.ComponentUtil

/**
 * Locale-aware helper for GUI labels and lore.
 *
 * Every user-visible piece of text in a GUI should flow through here so that:
 *   - localisation files own the wording (no hardcoded MiniMessage inside Kotlin)
 *   - PlaceholderAPI resolution is automatic when a Player is available
 *   - missing keys fall back to the key itself, surfacing locale gaps quickly during testing
 */
object GuiText {
    fun raw(key: String, placeholders: Map<String, Any?> = emptyMap()): String =
        SlateAPI.messageService()?.resolve(key, placeholders) ?: key

    fun component(key: String, placeholders: Map<String, Any?> = emptyMap()): Component =
        ComponentUtil.parse(raw(key, placeholders))

    fun component(player: Player, key: String, placeholders: Map<String, Any?> = emptyMap()): Component =
        ComponentUtil.parseWithPapi(raw(key, placeholders), player)

    fun lore(key: String, placeholders: Map<String, Any?> = emptyMap()): List<Component> {
        val resolved = raw(key, placeholders)
        if (resolved.isEmpty()) return emptyList()
        return resolved.split("\n").map(ComponentUtil::parse)
    }

    fun lore(player: Player, key: String, placeholders: Map<String, Any?> = emptyMap()): List<Component> {
        val resolved = raw(key, placeholders)
        if (resolved.isEmpty()) return emptyList()
        return resolved.split("\n").map { ComponentUtil.parseWithPapi(it, player) }
    }

    /**
     * Builds a lore list from multiple keys, each of which may itself contain `\n`-separated lines.
     * Empty resolutions are skipped so optional keys can render cleanly.
     */
    fun loreBlock(
        player: Player,
        vararg entries: Pair<String, Map<String, Any?>>,
    ): List<Component> = entries.flatMap { (key, placeholders) -> lore(player, key, placeholders) }
}
