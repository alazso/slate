package so.alaz.slate.crate

import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.config.ConfigValidator
import so.alaz.slate.config.PluginConfig
import so.alaz.slate.display.DisplayFactory

class CrateManagerReloadTest {
    private val tempDir = Files.createTempDirectory("slate-crate-reload-test")
    private val cratesDir = tempDir.resolve("crates")

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `reload retains last known good when file becomes invalid`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()

        val crateFile = cratesDir.resolve("alpha.yml")
        crateFile.toFile().writeText(validCrateYaml())

        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val first = manager.reload()
        assertEquals(1, first.filesUpdated)
        assertEquals(1, first.cratesLoaded)
        assertNotNull(manager.byId("alpha"))
        assertEquals(setOf("world", "minecraft:world", "minecraft:overworld"), manager.byId("alpha")?.allowedWorlds)

        crateFile.toFile().writeText("name: Broken\nkey:\n  material: TRIPWIRE_HOOK\n")
        val second = manager.reload()

        assertEquals(1, second.filesRetained)
        assertEquals(0, second.filesFailed)
        assertEquals(1, second.cratesLoaded)
        assertEquals(ReloadFileStatus.RETAINED_LAST_GOOD, second.fileOutcomes[crateFile.toFile().path]?.status)

        crateFile.toFile().delete()
        val third = manager.reload()
        assertEquals(1, third.filesRemoved)
        assertEquals(0, third.cratesLoaded)
    }

    @Test
    fun `reload parses crate display config`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()
        cratesDir.resolve("alpha.yml").toFile().writeText(
            validCrateYaml() + """

                display:
                  enabled: true
                  view-range: 48
                  billboard: center
                  item:
                    enabled: true
                    material: ENDER_CHEST
                    height-offset: 2.0
                    scale: 0.9
                  lines:
                    - "<gold>{crate_name}</gold>"
                    - "<gray>{key_amount}x {key_name}</gray>"
            """.trimIndent(),
        )

        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val summary = manager.reload()
        val crate = manager.byId("alpha")

        assertEquals(1, summary.cratesLoaded)
        assertEquals(true, crate?.display?.enabled)
        assertEquals(48.0f, crate?.display?.viewRange)
        assertEquals(org.bukkit.Material.ENDER_CHEST, crate?.display?.item?.material)
        assertEquals(2, crate?.display?.lines?.size)
    }

    @Test
    fun `reload parses lifecycle version and requirements`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()
        cratesDir.resolve("alpha.yml").toFile().writeText(
            validCrateYaml() + """

                crate-version: 1
                state: hidden
                requirements:
                  permissions:
                    open:
                      - slate.crate.alpha.open
                    list:
                      - slate.crate.alpha.list
                  regions:
                    allow:
                      - spawn
                    deny:
                      - no_crates
                    requireWorldGuard: true
            """.trimIndent(),
        )
        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val summary = manager.reload()
        val crate = manager.byId("alpha")

        assertEquals(1, summary.cratesLoaded)
        assertEquals(1, crate?.crateVersion)
        assertEquals(CrateState.HIDDEN, crate?.lifecycle?.state)
        assertEquals(setOf("slate.crate.alpha.open"), crate?.requirements?.permissions?.open)
        assertEquals(setOf("slate.crate.alpha.list"), crate?.requirements?.permissions?.list)
        assertEquals(setOf("spawn"), crate?.requirements?.regions?.allow)
        assertEquals(setOf("no_crates"), crate?.requirements?.regions?.deny)
        assertEquals(true, crate?.requirements?.regions?.requireWorldGuard)
    }

    @Test
    fun `reload parses preview and animation config`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()
        cratesDir.resolve("alpha.yml").toFile().writeText(
            validCrateYaml() + """

                animation:
                  enabled: true
                  template: csgo
                  tick-interval: 2
                  duration-ticks: 60
                  skip-permission: slate.animation.skip
                  decoy-count: 10
                preview:
                  enabled: true
                  rows: 6
                  border-material: BLACK_STAINED_GLASS_PANE
                  content-slots: "10-16,19-25"
                  nav-prev-slot: 48
                  nav-next-slot: 50
                  nav-close-slot: 49
                  info-slot: 4
            """.trimIndent(),
        )

        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val summary = manager.reload()
        val crate = manager.byId("alpha")

        assertEquals(1, summary.cratesLoaded)
        assertEquals(true, crate?.animation?.enabled)
        assertEquals("csgo", crate?.animation?.template)
        assertEquals(2, crate?.animation?.tickInterval)
        assertEquals(60, crate?.animation?.durationTicks)
        assertEquals(true, crate?.preview?.enabled)
        assertEquals(6, crate?.preview?.rows)
        assertEquals(14, crate?.preview?.contentSlots?.size)
    }

    @Test
    fun `docs demo crate stays loadable`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()
        val example = projectFile("docs/examples/astral.yml")
        Files.copy(example, cratesDir.resolve("astral.yml"), StandardCopyOption.REPLACE_EXISTING)
        assertTrue(Files.readString(cratesDir.resolve("astral.yml")).contains("Astral Reliquary"))

        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val summary = manager.reload()
        val crate = manager.byId("astral")

        assertEquals(1, summary.cratesLoaded)
        assertEquals(0, summary.cratesFailed)
        assertEquals("<gradient:#f97316:#facc15><bold>Astral Reliquary</bold></gradient>", crate?.name)
        assertEquals(11, crate?.prizes?.size)
        assertEquals(true, crate?.display?.enabled)
        assertEquals(true, crate?.pityConfig?.enabled)
    }

    @Test
    fun `crate display name placeholder uses configured name when filename and id differ`() {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        cratesDir.toFile().mkdirs()
        cratesDir.resolve("astral_reliquary.yml").toFile().writeText(
            validCrateYaml() + """

                id: demo
                name: "<gold>Astral Reliquary</gold>"
                display:
                  enabled: true
                  lines:
                    - "{crate_name}"
                    - "{crate_id}"
            """.trimIndent(),
        )

        val manager = CrateManager(plugin, ConfigValidator()) { PluginConfig() }
        val summary = manager.reload()
        val crate = manager.byId("astral_reliquary")

        assertEquals(1, summary.cratesLoaded)
        assertEquals("<gold>Astral Reliquary</gold>", crate?.let { DisplayFactory().renderCratePlaceholders(it, "{crate_name}") })
        assertEquals("astral_reliquary", crate?.let { DisplayFactory().renderCratePlaceholders(it, "{crate_id}") })
        assertEquals(
            "<gold>Astral Reliquary</gold>",
            crate?.copy(name = crate.id)?.let { DisplayFactory().renderCratePlaceholders(it, "{crate_name}") },
        )
    }

    private fun projectFile(relativePath: String): Path {
        val parts = relativePath.split("/")
        var current = Path.of("").toAbsolutePath()
        while (true) {
            val candidate = parts.fold(current) { path, part -> path.resolve(part) }
            if (Files.exists(candidate)) return candidate
            current = current.parent ?: error("Unable to locate $relativePath")
        }
    }

    private fun validCrateYaml(): String = """
        name: Alpha
        requiredKeys: 1
        maxPulls: -1
        cooldown: 0s
        allowedWorlds:
          - world
        key:
          material: TRIPWIRE_HOOK
        prizes:
          p1:
            weight: 1
            items:
              - DIAMOND
    """.trimIndent()
}
