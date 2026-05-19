package so.alaz.slate.config

data class CurrencyConfig(
    val defaultProvider: String = "auto",
    val defaultCurrency: String = "coins",
    val ledgerEnabled: Boolean = true,
    val vaultEnabled: Boolean = true,
    val vaultCurrencyId: String = "money",
    val vaultCurrencyName: String = "Money",
    val vaultCurrencySymbol: String? = "$",
    val vaultDecimals: Int = 2,
    val format: CurrencyFormatConfig = CurrencyFormatConfig(),
)

data class CurrencyFormatConfig(
    val standard: String = "us",
    val symbolPlacement: String = "prefix",
    val useGrouping: Boolean = true,
    val minFractionDigits: Int = 2,
    val maxFractionDigits: Int = 2,
    val defaultSymbol: String = "$",
    val customPattern: String? = null,
    val customDecimalSeparator: String = ".",
    val customGroupingSeparator: String = ",",
)
