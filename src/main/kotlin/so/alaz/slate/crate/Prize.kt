package so.alaz.slate.crate

data class Prize(
    val id: String,
    val name: String,
    val tier: String?,
    val weight: Int,
    val chance: Double? = null,
    val broadcast: Boolean,
    val broadcastMessage: String,
    val items: List<PrizeItem>,
    val commands: List<String>,
    val messages: List<String>,
    val grants: List<PrizeGrant> = emptyList(),
)

data class PrizeItem(
    val material: String = "",
    val amount: Int = 1,
    val adapter: String? = null,
    val itemId: String? = null,
    val serialized: String? = null,
    val customModelData: List<Float> = emptyList(),
    val pdc: Map<String, String> = emptyMap(),
)
