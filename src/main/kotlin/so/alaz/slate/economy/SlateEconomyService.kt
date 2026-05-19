package so.alaz.slate.economy

import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount

class SlateEconomyService(
    private val activeProvider: EconomyProvider,
    private val defaultProviderId: String,
    private val defaultCurrencyId: String,
    private val formatter: EconomyCurrencyFormatter,
) {
    fun provider(): EconomyProvider = activeProvider

    fun providers(): List<EconomyProvider> = listOf(activeProvider)

    fun currencies(providerId: String?): List<EconomyCurrency> {
        val resolved = resolveProvider(providerId) ?: return emptyList()
        return resolved.currencies().toList()
    }

    fun resolveProvider(providerId: String?): EconomyProvider? {
        val resolvedId = when {
            providerId.isNullOrBlank() || providerId == "default" -> defaultProviderId
            else -> providerId
        }
        return if (resolvedId == activeProvider.id) activeProvider else null
    }

    fun formatAmount(
        amount: CurrencyAmount,
        providerId: String?,
        currencyId: String?,
    ): String {
        val resolvedCurrencyId = currencyId.orDefault(defaultCurrencyId)
        val currency = resolveProvider(providerId)
            ?.currencies()
            ?.firstOrNull { it.id == resolvedCurrencyId }
        return formatter.format(amount, currency)
    }

    suspend fun getBalance(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
    ): EconomyBalanceResult {
        val provider = resolveProvider(providerId)
            ?: return EconomyBalanceResult.ProviderUnavailable(providerId ?: defaultProviderId, "provider not active")
        return provider.getBalance(account, currencyId.orDefault(defaultCurrencyId))
    }

    suspend fun deposit(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return EconomyMutationResult.ProviderUnavailable(providerId ?: defaultProviderId, "provider not active")
        return provider.deposit(account, currencyId.orDefault(defaultCurrencyId), amount, context)
    }

    suspend fun withdraw(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return EconomyMutationResult.ProviderUnavailable(providerId ?: defaultProviderId, "provider not active")
        return provider.withdraw(account, currencyId.orDefault(defaultCurrencyId), amount, context)
    }

    suspend fun setBalance(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: EconomyContext,
    ): EconomyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return EconomyMutationResult.ProviderUnavailable(providerId ?: defaultProviderId, "provider not active")
        return provider.setBalance(account, currencyId.orDefault(defaultCurrencyId), amount, context)
    }

    private fun String?.orDefault(defaultValue: String): String = this?.takeIf { it.isNotBlank() } ?: defaultValue
}

