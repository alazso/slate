package so.alaz.slate.key

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.SlateKeys
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.CratePlaceholderResolver
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.CrateType
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.item.ItemBuildRequest
import so.alaz.slate.item.ItemFactory
import so.alaz.slate.key.providers.ItemsAdderPhysicalKeyProvider
import so.alaz.slate.key.providers.NexoPhysicalKeyProvider
import so.alaz.slate.scheduler.BukkitMainDispatcher
import so.alaz.slate.scheduler.CancellableTask
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ComponentUtil

class KeyServiceImplTest {
    @Test
    fun `strict key definition match resolves crate`() {
        ensureSlateKeysInitialized()
        val crate = strictCrate()
        val crateManager = mockk<CrateManager>()
        val storage = mockk<StorageService>(relaxed = true)
        val service = KeyServiceImpl(crateManager, storage, mainDispatcher())
        every { crateManager.byId(crate.id) } returns crate

        val item = mockKeyItem(crate, amount = 2)

        val resolved = service.getCrateForKey(item)
        assertNotNull(resolved)
        assertEquals(crate.id, resolved!!.id)
    }

    @Test
    fun `strict key definition rejects near match`() {
        ensureSlateKeysInitialized()
        val crate = strictCrate()
        val crateManager = mockk<CrateManager>()
        val storage = mockk<StorageService>(relaxed = true)
        val service = KeyServiceImpl(crateManager, storage, mainDispatcher())
        every { crateManager.byId(crate.id) } returns crate

        val item = mockKeyItem(crate, amount = 2, lore = listOf(ComponentUtil.parse("<gray>Different lore</gray>")))

        val resolved = service.getCrateForKey(item)
        assertNull(resolved)
    }

    @Test
    fun `virtual consume delegates to atomic storage`() = runTest {
        ensureSlateKeysInitialized()
        val crate = strictCrate(requiredKeys = 3)
        val crateManager = mockk<CrateManager>(relaxed = true)
        val storage = mockk<StorageService>()
        val service = KeyServiceImpl(crateManager, storage, mainDispatcher())
        val player = mockk<Player>(relaxed = true)
        val playerId = UUID.randomUUID()
        every { player.uniqueId } returns playerId
        coEvery { storage.tryConsumeVirtualKeys(playerId, crate.id, crate.requiredKeys) } returns true

        val consumed = service.consumeKey(player, crate, KeyType.Virtual)

        assertTrue(consumed)
        verify(exactly = 0) { player.inventory }
    }

    @Test
    fun `physical hasKey counts matching stacks against requiredKeys`() = runTest {
        ensureSlateKeysInitialized()
        val crate = strictCrate(requiredKeys = 3)
        val crateManager = mockk<CrateManager>()
        val storage = mockk<StorageService>(relaxed = true)
        val service = KeyServiceImpl(crateManager, storage, mainDispatcher())
        every { crateManager.byId(crate.id) } returns crate

        val first = mockKeyItem(crate, amount = 1)
        val second = mockKeyItem(crate, amount = 2)

        val player = mockk<Player>(relaxed = true)
        every { player.inventory.contents } returns arrayOf(first, second)

        assertTrue(service.hasKey(player, crate, KeyType.Physical))
    }

    @Test
    fun `provider-backed physical keys request glowing overlay`() {
        val crate = strictCrate(provider = KeyProvider.Nexo, providerItemId = "slate:key")
        val item = mockk<ItemStack>(relaxed = true)
        val request = slot<ItemBuildRequest>()

        mockkObject(ItemFactory)
        try {
            every { ItemFactory.build(capture(request)) } returns item

            val minted = NexoPhysicalKeyProvider.mint(crate, 2)

            assertEquals(item, minted)
            assertEquals("nexo", request.captured.provider)
            assertEquals("slate:key", request.captured.itemId)
            assertEquals(2, request.captured.amount)
            assertEquals(true, request.captured.overlay.glint)
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    @Test
    fun `itemsadder physical keys resolve through shared item factory`() {
        val crate = strictCrate(provider = KeyProvider.ItemsAdder, providerItemId = "itemsadder:keys/strict_key")
        val item = mockk<ItemStack>(relaxed = true)
        val request = slot<ItemBuildRequest>()

        mockkObject(ItemFactory)
        try {
            every { ItemFactory.build(capture(request)) } returns item

            val minted = ItemsAdderPhysicalKeyProvider.mint(crate, 2)

            assertEquals(item, minted)
            assertEquals("itemsadder", request.captured.provider)
            assertEquals("itemsadder:keys/strict_key", request.captured.itemId)
            assertEquals(2, request.captured.amount)
            assertEquals(true, request.captured.overlay.glint)
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    @Test
    fun `physical key overlays resolve crate placeholders`() {
        val crate = strictCrate(provider = KeyProvider.ItemsAdder, providerItemId = "itemsadder:keys/strict_key").copy(
            name = "<gold>Astral Reliquary</gold>",
            keyDefinition = strictCrate(provider = KeyProvider.ItemsAdder, providerItemId = "itemsadder:keys/strict_key").keyDefinition.copy(
                name = "<yellow>{crate_name} Key</yellow>",
                lore = listOf("<gray>Open {crate_name}</gray>"),
            ),
        )
        val item = mockk<ItemStack>(relaxed = true)
        val request = slot<ItemBuildRequest>()

        mockkObject(ItemFactory)
        try {
            every { ItemFactory.build(capture(request)) } returns item

            ItemsAdderPhysicalKeyProvider.mint(crate, 1)

            assertEquals(
                CratePlaceholderResolver.render(crate.keyDefinition.name, crate),
                request.captured.overlay.name,
            )
            assertEquals(
                CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate),
                request.captured.overlay.lore,
            )
        } finally {
            unmockkObject(ItemFactory)
        }
    }

    private fun ensureSlateKeysInitialized() {
        val ready = runCatching { SlateKeys.KEY_CRATE_ID }.isSuccess
        if (!ready) {
            val plugin = mockk<Plugin>()
            every { plugin.name } returns "slatetest"
            every { plugin.namespace() } returns "slatetest"
            runCatching { SlateKeys.init(plugin) }
            check(runCatching { SlateKeys.KEY_CRATE_ID }.isSuccess) {
                "SlateKeys must be initialized for key tests"
            }
        }
    }

    private fun mockKeyItem(crate: CrateDefinition, amount: Int, lore: List<net.kyori.adventure.text.Component>? = null): ItemStack {
        val item = mockk<ItemStack>(relaxed = true)
        val meta = mockk<ItemMeta>(relaxed = true)
        val pdc = mockk<PersistentDataContainer>(relaxed = true)
        val effectiveLore = lore ?: CratePlaceholderResolver.renderLore(crate.keyDefinition.lore, crate).map(ComponentUtil::parse)

        every { item.type } returns crate.keyDefinition.material
        every { item.amount } returns amount
        every { item.itemMeta } returns meta
        every { meta.persistentDataContainer } returns pdc
        every { meta.displayName() } returns ComponentUtil.parse(CratePlaceholderResolver.render(crate.keyDefinition.name, crate))
        every { meta.lore() } returns effectiveLore
        every { meta.customModelData } returns crate.keyDefinition.customModelData.first().toInt()

        every { pdc.get(any(), any<PersistentDataType<*, *>>()) } answers {
            when (firstArg<org.bukkit.NamespacedKey>().key) {
                "is_key" -> 1.toByte()
                "crate_id" -> crate.id
                "custom_item_id" -> crate.keyDefinition.customItemId
                else -> null
            }
        }

        return item
    }

    private fun strictCrate(
        requiredKeys: Int = 1,
        provider: KeyProvider = KeyProvider.Vanilla,
        providerItemId: String? = null,
    ) = CrateDefinition(
        id = "strict",
        name = "Strict",
        type = CrateType.Unknown,
        requiredKeys = requiredKeys,
        keyDefinition = KeyDefinition(
            name = "<yellow>Strict Key</yellow>",
            lore = listOf("<gray>One true key</gray>"),
            material = Material.TRIPWIRE_HOOK,
            customModelData = listOf(1200f),
            customItemId = "minecraft:tripwire_hook",
            provider = provider,
            providerItemId = providerItemId,
            glowing = true,
        ),
        prizes = listOf(
            Prize(
                id = "p1",
                name = "Prize",
                tier = null,
                weight = 1,
                broadcast = false,
                broadcastMessage = "",
                items = emptyList(),
                commands = emptyList(),
                messages = emptyList(),
            ),
        ),
    )

    private fun mainDispatcher(): BukkitMainDispatcher {
        val scheduler = mockk<PlatformScheduler>()
        every { scheduler.runGlobal(any()) } answers {
            firstArg<() -> Unit>().invoke()
            object : CancellableTask {
                override fun cancel() {}
                override val isCancelled: Boolean = false
            }
        }
        return BukkitMainDispatcher(scheduler)
    }
}
