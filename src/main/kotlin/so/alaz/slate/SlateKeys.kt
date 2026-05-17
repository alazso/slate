package so.alaz.slate

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

object SlateKeys {
    private lateinit var plugin: Plugin

    fun init(plugin: Plugin) {
        check(!::plugin.isInitialized) { "SlateKeys already initialized" }
        this.plugin = plugin
    }

    val KEY_CRATE_ID: NamespacedKey get() = NamespacedKey(plugin, "crate_id")
    val KEY_IS_SLATE_KEY: NamespacedKey get() = NamespacedKey(plugin, "is_key")
    val KEY_PROVIDER_TYPE: NamespacedKey get() = NamespacedKey(plugin, "key_provider")
    val KEY_PROVIDER_ITEM_ID: NamespacedKey get() = NamespacedKey(plugin, "key_provider_item_id")
    val KEY_CUSTOM_ITEM_ID: NamespacedKey get() = NamespacedKey(plugin, "custom_item_id")
    val CRATE_BLOCK_ID: NamespacedKey get() = NamespacedKey(plugin, "crate_block_id")
    val DISPLAY_OWNED: NamespacedKey get() = NamespacedKey(plugin, "display_owned")
    val DISPLAY_GROUP_ID: NamespacedKey get() = NamespacedKey(plugin, "display_group_id")
    val DISPLAY_OWNER_TYPE: NamespacedKey get() = NamespacedKey(plugin, "display_owner_type")
    val DISPLAY_OWNER_ID: NamespacedKey get() = NamespacedKey(plugin, "display_owner_id")
    val DISPLAY_ELEMENT_TYPE: NamespacedKey get() = NamespacedKey(plugin, "display_element_type")
}
