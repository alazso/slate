package so.alaz.slate.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object ComponentUtil {
    private val miniMessage = MiniMessage.miniMessage()

    fun parse(input: String): Component = miniMessage.deserialize(input)

    fun parse(input: String, vararg placeholders: TagResolver): Component =
        miniMessage.deserialize(input, TagResolver.resolver(*placeholders))

    fun stripTags(input: String): String = miniMessage.stripTags(input)

    fun parseWithPapi(input: String, player: Player?): Component {
        val text = resolvePapi(input, player)
        return parse(text)
    }

    private fun resolvePapi(input: String, player: Player?): String {
        if (player == null) return input
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return input

        return try {
            val placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            val method = placeholderApiClass.getMethod("setPlaceholders", Player::class.java, String::class.java)
            method.invoke(null, player, input) as? String ?: input
        } catch (_: ReflectiveOperationException) {
            input
        }
    }
}
