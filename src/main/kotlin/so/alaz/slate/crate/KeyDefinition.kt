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

enum class KeyProvider {
    Vanilla,
    Nexo,
    CraftEngine,
    ;

    companion object {
        fun parse(raw: String?): KeyProvider? = when (raw?.trim()?.lowercase()) {
            null, "", "vanilla" -> Vanilla
            "nexo" -> Nexo
            "craftengine" -> CraftEngine
            else -> null
        }
    }
}
