package so.alaz.slate.currency.domain

import java.math.BigInteger

@JvmInline
value class CurrencyAmount private constructor(val minorUnits: BigInteger) : Comparable<CurrencyAmount> {
    override fun compareTo(other: CurrencyAmount): Int = minorUnits.compareTo(other.minorUnits)

    operator fun plus(other: CurrencyAmount): CurrencyAmount = CurrencyAmount(minorUnits + other.minorUnits)

    operator fun minus(other: CurrencyAmount): CurrencyAmount = CurrencyAmount(minorUnits - other.minorUnits)

    fun multipliedBy(factor: Int): CurrencyAmount = CurrencyAmount(minorUnits * BigInteger.valueOf(factor.toLong()))

    fun isNegative(): Boolean = minorUnits < BigInteger.ZERO

    fun isZero(): Boolean = minorUnits == BigInteger.ZERO

    fun asPersistedString(): String = minorUnits.toString()

    companion object {
        fun ofMinorUnits(value: Long): CurrencyAmount = CurrencyAmount(BigInteger.valueOf(value))

        fun ofMinorUnits(value: BigInteger): CurrencyAmount = CurrencyAmount(value)

        fun fromPersistedString(value: String): CurrencyAmount = CurrencyAmount(value.trim().toBigInteger())

        fun zero(): CurrencyAmount = CurrencyAmount(BigInteger.ZERO)
    }
}
