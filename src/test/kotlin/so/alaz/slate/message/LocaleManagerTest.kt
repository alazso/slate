package so.alaz.slate.message

import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import kotlin.io.path.writeText
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocaleManagerTest {
    private val tempDir = Files.createTempDirectory("slate-locale-test")

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `falls back to en locale when requested locale missing`() {
        val plugin = mockPlugin()
        val localeManager = LocaleManager(plugin)
        localeManager.load("fr")

        assertEquals(
            "<red>Slate is not initialized.</red>",
            localeManager.string("messages.chat.common.not_initialized"),
        )
    }

    @Test
    fun `loads requested locale when file exists`() {
        val plugin = mockPlugin()
        val localesDir = tempDir.resolve("locales").toFile().apply { mkdirs() }
        localesDir.resolve("fr.yml").toPath().writeText(
            """
            messages:
              chat:
                common:
                  not_initialized: "<red>FR not initialized</red>"
            """.trimIndent(),
        )
        val localeManager = LocaleManager(plugin)
        localeManager.load("fr")
        assertEquals("<red>FR not initialized</red>", localeManager.string("messages.chat.common.not_initialized"))
    }

    private fun mockPlugin(): JavaPlugin {
        val plugin = mockk<JavaPlugin>()
        every { plugin.dataFolder } returns tempDir.toFile()
        every { plugin.saveResource(any(), any()) } answers {
            val resourcePath = firstArg<String>()
            val target = tempDir.resolve(resourcePath).toFile()
            target.parentFile?.mkdirs()
            if (!target.exists()) {
                target.writeText(
                    """
                    messages:
                      chat:
                        common:
                          not_initialized: "<red>Slate is not initialized.</red>"
                    """.trimIndent(),
                )
            }
        }
        return plugin
    }
}
