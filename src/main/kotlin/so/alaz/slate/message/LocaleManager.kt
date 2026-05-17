package so.alaz.slate.message

import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class LocaleManager(
    private val plugin: JavaPlugin,
) {
    private var fallback: YamlConfiguration = YamlConfiguration()
    private var active: YamlConfiguration = YamlConfiguration()
    private var activeLanguage: String = "en"

    fun load(language: String) {
        ensureDefaultLocale()
        fallback = loadLocaleFile("en")
        activeLanguage = language.trim().ifBlank { "en" }
        active = loadLocaleFile(activeLanguage)
    }

    fun reload(language: String) = load(language)

    fun string(key: String): String? =
        active.getString(key) ?: fallback.getString(key)

    fun int(key: String): Int? =
        when {
            active.contains(key) -> active.getInt(key)
            fallback.contains(key) -> fallback.getInt(key)
            else -> null
        }

    private fun ensureDefaultLocale() {
        val localesDir = File(plugin.dataFolder, "locales")
        if (!localesDir.exists()) localesDir.mkdirs()
        val defaultFile = File(localesDir, "en.yml")
        if (!defaultFile.exists()) {
            plugin.saveResource("locales/en.yml", false)
        }
    }

    private fun loadLocaleFile(language: String): YamlConfiguration {
        val localesDir = File(plugin.dataFolder, "locales").apply { mkdirs() }
        val requested = File(localesDir, "$language.yml")
        val target = if (requested.exists()) requested else File(localesDir, "en.yml")
        return YamlConfiguration.loadConfiguration(target)
    }
}
