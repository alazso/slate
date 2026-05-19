package so.alaz.slate.crate

import so.alaz.slate.currency.domain.CurrencyAmount

sealed interface CrateCost {
    data class CurrencyCost(
        val provider: String = "slate",
        val currency: String,
        val amount: CurrencyAmount,
    ) : CrateCost
}

sealed interface PrizeGrant {
    data class CurrencyGrant(
        val provider: String = "slate",
        val currency: String,
        val amount: CurrencyAmount,
    ) : PrizeGrant
}
