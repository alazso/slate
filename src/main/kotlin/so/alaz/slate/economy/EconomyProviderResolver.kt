package so.alaz.slate.economy

import org.bukkit.Server
import so.alaz.slate.config.CurrencyConfig
import so.alaz.slate.economy.provider.LedgerEconomyProvider
import so.alaz.slate.economy.provider.NoOpEconomyProvider
import so.alaz.slate.economy.provider.VaultEconomyProvider
import so.alaz.slate.util.LogUtil

class EconomyProviderResolver(
    private val server: Server,
    private val config: CurrencyConfig,
) {
    fun resolve(): EconomyProvider {
        val normalized = config.defaultProvider.lowercase()
        val preferredOrder = when (normalized) {
            "ledger" -> listOf("ledger", "vault", "noop")
            "vault" -> listOf("vault", "ledger", "noop")
            else -> listOf("ledger", "vault", "noop")
        }
        preferredOrder.forEach { candidate ->
            val provider = when (candidate) {
                "ledger" -> buildLedgerProvider()
                "vault" -> buildVaultProvider()
                else -> null
            }
            if (provider != null && provider.isAvailable()) {
                return provider
            }
        }
        return NoOpEconomyProvider()
    }

    private fun buildLedgerProvider(): EconomyProvider? {
        if (!config.ledgerEnabled) return null
        val plugin = server.pluginManager.getPlugin("Ledger")
        if (plugin == null || !plugin.isEnabled) return null
        return runCatching { LedgerEconomyProvider(server) }
            .onFailure { LogUtil.warn("Ledger provider load failed: ${it.message}") }
            .getOrNull()
    }

    private fun buildVaultProvider(): EconomyProvider? {
        if (!config.vaultEnabled) return null
        val plugin = server.pluginManager.getPlugin("Vault")
        if (plugin == null || !plugin.isEnabled) return null
        return VaultEconomyProvider(
            server = server,
            currencyId = config.vaultCurrencyId,
            currencyName = config.vaultCurrencyName,
            currencySymbol = config.vaultCurrencySymbol,
            decimals = config.vaultDecimals,
        )
    }
}

