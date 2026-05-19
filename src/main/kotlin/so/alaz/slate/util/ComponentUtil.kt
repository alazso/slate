package so.alaz.slate.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

object ComponentUtil {
    private val miniMessage = MiniMessage.miniMessage()
    @Volatile
    private var papiSetPlaceholdersMethod: java.lang.reflect.Method? = null
    @Volatile
    private var papiMethodLookupAttempted: Boolean = false

    fun parse(input: String): Component = miniMessage.deserialize(resolvePapi(input, null))

    fun parse(input: String, vararg placeholders: TagResolver): Component =
        miniMessage.deserialize(resolvePapi(input, null), TagResolver.resolver(*placeholders))

    fun stripTags(input: String): String = miniMessage.stripTags(input)

    fun parseWithPapi(input: String, player: Player?): Component {
        val text = resolvePapi(input, player)
        return parse(text)
    }

    fun parseWithPapi(input: String, player: OfflinePlayer?): Component {
        val text = resolvePapi(input, player)
        return parse(text)
    }

    private fun resolvePapi(input: String, player: OfflinePlayer?): String {
        val papiEnabled = runCatching {
            Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
        }.getOrDefault(false)
        if (!papiEnabled) return input

        val method = resolvePapiMethod() ?: return input
        return try {
            method.invoke(null, player, input) as? String ?: input
        } catch (_: ReflectiveOperationException) {
            input
        }
    }

    private fun resolvePapiMethod(): java.lang.reflect.Method? {
        papiSetPlaceholdersMethod?.let { return it }
        if (papiMethodLookupAttempted) return null
        return synchronized(this) {
            papiSetPlaceholdersMethod?.let { return@synchronized it }
            if (papiMethodLookupAttempted) return@synchronized null
            papiMethodLookupAttempted = true
            runCatching {
                val placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                placeholderApiClass.getMethod("setPlaceholders", OfflinePlayer::class.java, String::class.java)
            }.getOrNull()?.also {
                papiSetPlaceholdersMethod = it
            }
        }
    }
}
