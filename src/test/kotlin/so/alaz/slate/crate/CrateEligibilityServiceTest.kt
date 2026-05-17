package so.alaz.slate.crate

import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.display.CrateDisplayConfig
import so.alaz.slate.opening.FailureReason

class CrateEligibilityServiceTest {
    @Test
    fun `hidden crate is excluded from list visibility`() {
        val service = CrateEligibilityService()
        val sender = mockk<CommandSender>(relaxed = true)
        val crate = testCrate().copy(lifecycle = CrateLifecycleConfig(state = CrateState.HIDDEN))
        assertEquals(CrateVisibility.HIDDEN, service.visibilityFor(sender, crate))
    }

    @Test
    fun `seasonal crate is hidden outside window`() {
        val now = Instant.parse("2026-12-05T00:00:00Z")
        val service = CrateEligibilityService(clock = { now })
        val sender = mockk<CommandSender>(relaxed = true)
        val crate = testCrate().copy(
            lifecycle = CrateLifecycleConfig(
                state = CrateState.SEASONAL,
                seasonalWindows = listOf(
                    SeasonalWindow(
                        startsAt = Instant.parse("2026-11-01T00:00:00Z"),
                        endsAt = Instant.parse("2026-11-02T00:00:00Z"),
                    ),
                ),
            ),
        )
        assertEquals(CrateVisibility.HIDDEN, service.visibilityFor(sender, crate))
    }

    @Test
    fun `open is blocked when region requirement exists and worldguard unavailable`() {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.environment } returns World.Environment.NORMAL
        every { player.world } returns world
        every { player.hasPermission(any<String>()) } returns true
        val crate = testCrate().copy(
            requirements = CrateRequirements(
                regions = CrateRegionRequirements(allow = setOf("spawn")),
            ),
        )
        val service = CrateEligibilityService(regionAccessService = NoopRegionAccessService)
        val eligibility = service.canOpen(player, crate)
        assertFalse(eligibility.allowed)
        assertEquals(FailureReason.WORLDGUARD_UNAVAILABLE, eligibility.failureReason)
    }

    @Test
    fun `preview requires additive preview permission`() {
        val sender = mockk<CommandSender>(relaxed = true)
        every { sender.hasPermission("slate.crate.demo.preview") } returns false
        val crate = testCrate().copy(
            requirements = CrateRequirements(
                permissions = CratePermissionRequirements(preview = setOf("slate.crate.demo.preview")),
            ),
        )
        val service = CrateEligibilityService()
        val eligibility = service.canPreview(sender, crate)
        assertFalse(eligibility.allowed)
        assertEquals(FailureReason.MISSING_PERMISSION, eligibility.failureReason)
    }

    @Test
    fun `open succeeds with default active crate`() {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.environment } returns World.Environment.NORMAL
        every { player.world } returns world
        every { player.hasPermission(any<String>()) } returns true
        val service = CrateEligibilityService()
        val eligibility = service.canOpen(player, testCrate())
        assertTrue(eligibility.allowed)
    }

    private fun testCrate(): CrateDefinition = CrateDefinition(
        id = "demo",
        name = "Demo",
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
        display = CrateDisplayConfig(),
    )
}

