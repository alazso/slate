package so.alaz.slate.config

data class PluginConfig(
    val configVersion: Int = 1,
    @Volatile var debug: Boolean = false, // Mutable: runtime debug toggle via /sl debug.
    val language: String = "en",
    val broadcastPrefix: String = "<gold>[Slate]</gold> ",
    val crateScanRecursive: Boolean = false,
    val maxBulkOpenAmount: Int = 10,
    val gui: GuiConfig = GuiConfig(),
    val anchors: AnchorProtectionConfig = AnchorProtectionConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
    val cluster: ClusterConfig = ClusterConfig(),
    val currency: CurrencyConfig = CurrencyConfig(),
)
