package so.alaz.slate.currency.service

import java.util.concurrent.ConcurrentHashMap
import org.bukkit.plugin.Plugin
import org.bukkit.Server
import org.bukkit.plugin.ServicePriority
import so.alaz.slate.currency.domain.BalanceResult
import so.alaz.slate.currency.domain.CurrencyAccount
import so.alaz.slate.currency.domain.CurrencyAmount
import so.alaz.slate.currency.domain.CurrencyDefinition
import so.alaz.slate.currency.domain.CurrencyMutationResult
import so.alaz.slate.currency.domain.CurrencyTransaction
import so.alaz.slate.currency.domain.CurrencyTransactionContext
import so.alaz.slate.currency.provider.InternalSlateCurrencyProvider
import so.alaz.slate.currency.spi.SlateCurrencyProvider
import so.alaz.slate.currency.storage.CurrencyStorage

class SlateCurrencyService(
    private val server: Server,
    private val plugin: Plugin,
    private val pluginProviderId: String = "slate",
    private val defaultProviderResolver: () -> String,
    private val defaultCurrencyResolver: () -> String,
    private val currencyStorage: CurrencyStorage,
) {
    private val providers = ConcurrentHashMap<String, SlateCurrencyProvider>()
    private val internalCurrencies = ConcurrentHashMap<String, CurrencyDefinition>()
    private val internalProvider = InternalSlateCurrencyProvider(currencyStorage) { internalCurrencies.values.toList() }

    fun registerInternalProvider() {
        registerProvider(internalProvider)
        server.servicesManager.register(
            SlateCurrencyProvider::class.java,
            internalProvider,
            plugin,
            ServicePriority.Lowest,
        )
    }

    fun discoverExternalProviders() {
        server.servicesManager.getRegistrations(SlateCurrencyProvider::class.java)
            .map { it.provider }
            .filter { it.id != pluginProviderId }
            .forEach(::registerProvider)
    }

    fun registerProvider(provider: SlateCurrencyProvider) {
        providers[provider.id] = provider
    }

    fun upsertInternalCurrencies(definitions: Collection<CurrencyDefinition>) {
        internalCurrencies.clear()
        definitions.forEach { definition ->
            internalCurrencies[definition.id.value] = definition
        }
    }

    suspend fun persistInternalCurrencies() {
        internalCurrencies.values.forEach { currencyStorage.upsertCurrencyDefinition(it) }
    }

    fun providers(): List<SlateCurrencyProvider> = providers.values.sortedBy { it.id }

    fun currencies(providerId: String): List<CurrencyDefinition> =
        resolveProvider(providerId)?.currencies()?.sortedBy { it.id.value } ?: emptyList()

    fun resolveProvider(providerId: String?): SlateCurrencyProvider? {
        val effectiveId = when {
            providerId.isNullOrBlank() || providerId == "default" -> defaultProviderResolver()
            else -> providerId
        }
        return providers[effectiveId]
    }

    suspend fun getBalance(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
    ): BalanceResult {
        val provider = resolveProvider(providerId)
            ?: return BalanceResult.ProviderUnavailable(providerId ?: "default")
        val resolvedCurrency = currencyId?.takeIf { it.isNotBlank() } ?: defaultCurrencyResolver()
        return provider.getBalance(account, resolvedCurrency)
    }

    suspend fun deposit(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return CurrencyMutationResult.ProviderUnavailable(providerId ?: "default")
        val resolvedCurrency = currencyId?.takeIf { it.isNotBlank() } ?: defaultCurrencyResolver()
        return provider.deposit(account, resolvedCurrency, amount, context)
    }

    suspend fun withdraw(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return CurrencyMutationResult.ProviderUnavailable(providerId ?: "default")
        val resolvedCurrency = currencyId?.takeIf { it.isNotBlank() } ?: defaultCurrencyResolver()
        return provider.withdraw(account, resolvedCurrency, amount, context)
    }

    suspend fun setBalance(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        amount: CurrencyAmount,
        context: CurrencyTransactionContext,
    ): CurrencyMutationResult {
        val provider = resolveProvider(providerId)
            ?: return CurrencyMutationResult.ProviderUnavailable(providerId ?: "default")
        val resolvedCurrency = currencyId?.takeIf { it.isNotBlank() } ?: defaultCurrencyResolver()
        return provider.setBalance(account, resolvedCurrency, amount, context)
    }

    suspend fun history(
        account: CurrencyAccount,
        providerId: String?,
        currencyId: String?,
        limit: Int,
    ): List<CurrencyTransaction> = currencyStorage.listTransactions(account, providerId, currencyId, limit)
}
