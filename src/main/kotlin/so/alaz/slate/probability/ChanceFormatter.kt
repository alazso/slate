package so.alaz.slate.probability

import java.text.DecimalFormat
import kotlin.math.abs

object ChanceFormatter {
    private val defaultPercentFormatter = DecimalFormat("0.####")
    private val lowPercentFormatter = DecimalFormat("0.########")

    fun percent(chance: Double): String {
        val clamped = chance.coerceIn(0.0, 1.0) * 100.0
        val formatter = if (clamped in 0.0..0.1) lowPercentFormatter else defaultPercentFormatter
        return "${formatter.format(clamped)}%"
    }

    fun ratio(chance: Double): String {
        val normalized = chance.coerceIn(0.0, 1.0)
        if (normalized <= 0.0) return "never"
        if (abs(normalized - 1.0) < 1.0e-12) return "always"
        val n = 1.0 / normalized
        val display = if (n >= 1000.0) DecimalFormat("0").format(n) else DecimalFormat("0.##").format(n)
        return "1 in $display"
    }

    fun display(chance: Double, hidden: Boolean): String =
        if (hidden) "hidden" else "${percent(chance)} (${ratio(chance)})"
}
