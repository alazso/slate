package so.alaz.slate.config

data class AnchorProtectionConfig(
    val breakConfirmEnabled: Boolean = false,
    val breakConfirmWindowSeconds: Long = 5L,
)

