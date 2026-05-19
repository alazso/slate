package so.alaz.slate.economy

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.ledger.api.LedgerService
import so.alaz.slate.config.CurrencyConfig
import net.milkbowl.vault.economy.Economy

class EconomyProviderResolverTest {
    @Test
    fun `resolver prefers ledger before vault`() {
        val server = mockServer(ledgerAvailable = true, vaultAvailable = true)
        val provider = EconomyProviderResolver(server, CurrencyConfig(defaultProvider = "auto")).resolve()
        assertEquals("ledger", provider.id)
    }

    @Test
    fun `resolver falls back to vault when ledger missing`() {
        val server = mockServer(ledgerAvailable = false, vaultAvailable = true)
        val provider = EconomyProviderResolver(server, CurrencyConfig(defaultProvider = "auto")).resolve()
        assertEquals("vault", provider.id)
    }

    @Test
    fun `resolver falls back to noop when no backends are available`() {
        val server = mockServer(ledgerAvailable = false, vaultAvailable = false)
        val provider = EconomyProviderResolver(server, CurrencyConfig(defaultProvider = "auto")).resolve()
        assertEquals("noop", provider.id)
    }

    private fun mockServer(ledgerAvailable: Boolean, vaultAvailable: Boolean): Server {
        val pluginManager = mockk<PluginManager>()
        val servicesManager = mockk<ServicesManager>()
        val server = mockk<Server>()
        every { server.pluginManager } returns pluginManager
        every { server.servicesManager } returns servicesManager

        val ledgerPlugin = mockk<Plugin>(relaxed = true)
        every { ledgerPlugin.isEnabled } returns ledgerAvailable
        every { pluginManager.getPlugin("Ledger") } returns ledgerPlugin.takeIf { ledgerAvailable }

        val vaultPlugin = mockk<Plugin>(relaxed = true)
        every { vaultPlugin.isEnabled } returns vaultAvailable
        every { pluginManager.getPlugin("Vault") } returns vaultPlugin.takeIf { vaultAvailable }

        every { servicesManager.load(LedgerService::class.java) } returns mockk<LedgerService>().takeIf { ledgerAvailable }

        val vaultEconomy = mockk<Economy>(relaxed = true)
        val registration = mockk<RegisteredServiceProvider<Economy>>()
        every { registration.provider } returns vaultEconomy
        every { servicesManager.getRegistration(Economy::class.java) } returns registration.takeIf { vaultAvailable }

        return server
    }
}

