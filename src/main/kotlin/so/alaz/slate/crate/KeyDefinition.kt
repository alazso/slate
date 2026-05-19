package so.alaz.slate.crate

import org.bukkit.Material

data class KeyDefinition(
    val name: String,
    val lore: List<String>,
    val material: Material,
    val customModelData: List<Float>,
    val customItemId: String?,
    val provider: KeyProvider,
    val providerItemId: String?,
    val glowing: Boolean,
)

enum class KeyProvider(
    val providerId: String,
) {
    Vanilla("vanilla"),
    Nexo("nexo"),
    CraftEngine("craftengine"),
    ItemsAdder("itemsadder"),
    ;

    companion object {
        fun parse(raw: String?): KeyProvider? {
            val normalized = raw?.trim()?.lowercase()
            if (normalized.isNullOrBlank()) return Vanilla
            return entries.firstOrNull { it.providerId == normalized }
        }
    }
}
