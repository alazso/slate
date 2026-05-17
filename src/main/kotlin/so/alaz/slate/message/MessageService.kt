package so.alaz.slate.message

import net.kyori.adventure.title.Title
import net.kyori.adventure.util.Ticks
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import so.alaz.slate.config.PluginConfig
import so.alaz.slate.util.ComponentUtil

class MessageService(
    private val localeManager: LocaleManager,
    private val configProvider: () -> PluginConfig,
) {
    fun reload(language: String) {
        localeManager.reload(language)
    }

    fun chat(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, Any?> = emptyMap(),
    ) {
        val template = localeManager.string(key) ?: key
        val rendered = applyPlaceholders(template, placeholders)
        val prefixed = "${configProvider().broadcastPrefix}$rendered"
        val component = if (sender is Player) {
            ComponentUtil.parseWithPapi(prefixed, sender)
        } else {
            ComponentUtil.parse(prefixed)
        }
        sender.sendMessage(component)
    }

    fun chatRaw(
        sender: CommandSender,
        template: String,
        placeholders: Map<String, Any?> = emptyMap(),
        withPrefix: Boolean = false,
    ) {
        val rendered = applyPlaceholders(template, placeholders)
        val text = if (withPrefix) "${configProvider().broadcastPrefix}$rendered" else rendered
        val component = if (sender is Player) {
            ComponentUtil.parseWithPapi(text, sender)
        } else {
            ComponentUtil.parse(text)
        }
        sender.sendMessage(component)
    }

    fun hotbar(
        player: Player,
        key: String,
        placeholders: Map<String, Any?> = emptyMap(),
    ) {
        val template = localeManager.string(key) ?: key
        val rendered = applyPlaceholders(template, placeholders)
        player.sendActionBar(ComponentUtil.parseWithPapi(rendered, player))
    }

    fun title(
        player: Player,
        keyBase: String,
        placeholders: Map<String, Any?> = emptyMap(),
    ) {
        val titleTemplate = localeManager.string("$keyBase.title") ?: return
        val subtitleTemplate = localeManager.string("$keyBase.subtitle").orEmpty()
        val fadeIn = localeManager.int("$keyBase.fadeIn") ?: 10
        val stay = localeManager.int("$keyBase.stay") ?: 60
        val fadeOut = localeManager.int("$keyBase.fadeOut") ?: 10

        val title = applyPlaceholders(titleTemplate, placeholders)
        val subtitle = applyPlaceholders(subtitleTemplate, placeholders)

        player.showTitle(
            Title.title(
                ComponentUtil.parseWithPapi(title, player),
                ComponentUtil.parseWithPapi(subtitle, player),
                Title.Times.times(
                    Ticks.duration(fadeIn.toLong()),
                    Ticks.duration(stay.toLong()),
                    Ticks.duration(fadeOut.toLong()),
                ),
            ),
        )
    }

    fun resolve(
        key: String,
        placeholders: Map<String, Any?> = emptyMap(),
    ): String {
        val template = localeManager.string(key) ?: key
        return applyPlaceholders(template, placeholders)
    }

    private fun applyPlaceholders(
        template: String,
        placeholders: Map<String, Any?>,
    ): String {
        var result = template
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value?.toString().orEmpty())
        }
        return result
    }
}
