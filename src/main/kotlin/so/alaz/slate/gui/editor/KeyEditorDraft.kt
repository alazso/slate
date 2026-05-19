package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemOverlay

internal data class KeyEditorDraft(
    var name: String,
    var lore: List<String>,
    var material: Material,
    var customModelData: List<Float>,
    var customItemId: String?,
    var provider: KeyProvider,
    var providerItemId: String?,
    var glowing: Boolean,
) {
    companion object {
        fun fromCrate(crate: CrateDefinition): KeyEditorDraft {
            val key = crate.keyDefinition
            return KeyEditorDraft(
                name = key.name,
                lore = key.lore,
                material = key.material,
                customModelData = key.customModelData,
                customItemId = key.customItemId,
                provider = key.provider,
                providerItemId = key.providerItemId,
                glowing = key.glowing,
            )
        }
    }

    fun toBuildRequest(includeTextOverlay: Boolean): ItemBuildRequest =
        ItemBuildRequest(
            provider = if (provider == KeyProvider.Vanilla) "vanilla" else provider.providerId,
            material = material.name,
            itemId = providerItemId,
            amount = 1,
            overlay = ItemOverlay(
                name = name.takeIf { includeTextOverlay && it.isNotBlank() },
                lore = lore.takeIf { includeTextOverlay && it.isNotEmpty() },
                glint = glowing,
                customModelData = customModelData,
                itemModel = customItemId,
            ),
        )

    fun writeToYaml(yaml: YamlConfiguration) {
        yaml.set("key.name", name)
        yaml.set("key.lore", lore)
        yaml.set("key.material", material.name)
        yaml.set("key.provider", provider.providerId)
        yaml.set("key.providerItemId", providerItemId?.takeIf { it.isNotBlank() })
        yaml.set("key.customModelData", customModelData.takeIf { it.isNotEmpty() })
        yaml.set("key.customItemId", customItemId?.takeIf { it.isNotBlank() })
        yaml.set("key.glowing", glowing)
    }
}
