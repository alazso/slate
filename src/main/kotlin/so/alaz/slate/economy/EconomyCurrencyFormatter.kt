package so.alaz.slate.economy

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import so.alaz.slate.config.CurrencyFormatConfig
import so.alaz.slate.currency.domain.CurrencyAmount

class EconomyCurrencyFormatter(
    private val config: CurrencyFormatConfig,
) {
    fun format(
        amount: CurrencyAmount,
        currency: EconomyCurrency?,
    ): String {
        val scale = currency?.decimals ?: config.maxFractionDigits
        val value = amount.minorUnits.toBigDecimal().movePointLeft(scale)
        val symbol = currency?.symbol ?: config.defaultSymbol
        val number = when (config.standard.lowercase()) {
            "custom" -> formatCustom(value, scale)
            else -> formatStandard(value, scale)
        }
        return when (config.symbolPlacement.lowercase()) {
            "suffix" -> "$number$symbol"
            else -> "$symbol$number"
        }
    }

    private fun formatStandard(value: BigDecimal, scale: Int): String {
        val locale = when (config.standard.lowercase()) {
            "uk" -> Locale.UK
            "eu" -> Locale.GERMANY
            "jp" -> Locale.JAPAN
            "in" -> Locale("en", "IN")
            "ch" -> Locale("de", "CH")
            else -> Locale.US
        }
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.isGroupingUsed = config.useGrouping
        formatter.minimumFractionDigits = config.minFractionDigits.coerceAtMost(scale)
        formatter.maximumFractionDigits = config.maxFractionDigits.coerceAtLeast(scale)
        return formatter.format(value)
    }

    private fun formatCustom(value: BigDecimal, scale: Int): String {
        val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
            decimalSeparator = config.customDecimalSeparator.firstOrNull() ?: '.'
            groupingSeparator = config.customGroupingSeparator.firstOrNull() ?: ','
        }
        val formatter = DecimalFormat(config.customPattern ?: "#,##0.00", symbols)
        formatter.isGroupingUsed = config.useGrouping
        formatter.minimumFractionDigits = config.minFractionDigits.coerceAtMost(scale)
        formatter.maximumFractionDigits = config.maxFractionDigits.coerceAtLeast(scale)
        return formatter.format(value)
    }
}

