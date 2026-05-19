package so.alaz.slate.economy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.config.CurrencyFormatConfig
import so.alaz.slate.currency.domain.CurrencyAmount

class EconomyCurrencyFormatterTest {
    @Test
    fun `formats amount using US standard`() {
        val formatter = EconomyCurrencyFormatter(
            CurrencyFormatConfig(
                standard = "us",
                symbolPlacement = "prefix",
                minFractionDigits = 2,
                maxFractionDigits = 2,
                defaultSymbol = "$",
            ),
        )

        val rendered = formatter.format(
            CurrencyAmount.ofMinorUnits(1_000_000),
            EconomyCurrency(id = "money", name = "Money", symbol = "$", decimals = 2),
        )

        assertEquals("$10,000.00", rendered)
    }

    @Test
    fun `formats amount using EU standard`() {
        val formatter = EconomyCurrencyFormatter(
            CurrencyFormatConfig(
                standard = "eu",
                symbolPlacement = "suffix",
                minFractionDigits = 2,
                maxFractionDigits = 2,
                defaultSymbol = "€",
            ),
        )

        val rendered = formatter.format(
            CurrencyAmount.ofMinorUnits(1_000_000),
            EconomyCurrency(id = "euro", name = "Euro", symbol = "€", decimals = 2),
        )

        assertEquals("10.000,00€", rendered)
    }

    @Test
    fun `formats amount using custom separators and pattern`() {
        val formatter = EconomyCurrencyFormatter(
            CurrencyFormatConfig(
                standard = "custom",
                symbolPlacement = "prefix",
                minFractionDigits = 2,
                maxFractionDigits = 2,
                defaultSymbol = "$",
                customPattern = "#,##0.00",
                customDecimalSeparator = ",",
                customGroupingSeparator = " ",
            ),
        )

        val rendered = formatter.format(
            CurrencyAmount.ofMinorUnits(1_000_000),
            EconomyCurrency(id = "money", name = "Money", symbol = "$", decimals = 2),
        )

        assertEquals("$10 000,00", rendered)
    }
}

