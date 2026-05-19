package so.alaz.slate.placeholder

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.UUID
import org.bukkit.Material
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.key.KeyService
import so.alaz.slate.storage.StorageService

class PlaceholderResolverServiceTest {
    @AfterEach
    fun teardown() {
        unmockkObject(SlateAPI)
    }

    @Test
    fun `resolves keys for online player with crate`() {
        val player = mockk<Player>()
        val keyService = mockk<KeyService>()
        val storage = mockk<StorageService>()
        val crateManager = mockk<CrateManager>()
        val crate = testCrate("astral")
        val uuid = UUID.randomUUID()

        every { player.uniqueId } returns uuid
        every { crateManager.byId("astral") } returns crate
        coEvery { keyService.getVirtualKeys(uuid, "astral") } returns 7
        coEvery { keyService.countKeys(player, crate, so.alaz.slate.key.KeyType.Physical) } returns 3

        mockkObject(SlateAPI)
        every { SlateAPI.crateManager() } returns crateManager
        every { SlateAPI.keyService() } returns keyService
        every { SlateAPI.storageService() } returns storage

        val resolver = PlaceholderResolverService()
        assertEquals("10", resolver.resolve(player, "keys:astral"))
    }

    @Test
    fun `returns fallback for unknown crate and offline context`() {
        val crateManager = mockk<CrateManager>()

        every { crateManager.byId(any()) } returns null

        mockkObject(SlateAPI)
        every { SlateAPI.crateManager() } returns crateManager
        every { SlateAPI.keyService() } returns null
        every { SlateAPI.storageService() } returns null

        val resolver = PlaceholderResolverService()
        assertEquals(null, resolver.resolve(null, "keys_astral"))
        assertEquals("N/A", resolver.resolve(mockk(), "keys:unknown"))
    }

    private fun testCrate(id: String): CrateDefinition =
        CrateDefinition(
            id = id,
            name = "Astral",
            keyDefinition = KeyDefinition(
                name = "Key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            prizes = emptyList(),
        )
}
