package so.alaz.slate.message

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import so.alaz.slate.config.PluginConfig

class MessageServiceTest {
    @Test
    fun `resolve applies placeholders`() {
        val locale = mockk<LocaleManager>()
        every { locale.string("test.key") } returns "Hello {name}, crate={crate}"
        val service = MessageService(locale) { PluginConfig() }

        val rendered = service.resolve("test.key", mapOf("name" to "Alazoodle", "crate" to "slate"))
        assertEquals("Hello Alazoodle, crate=slate", rendered)
    }

    @Test
    fun `resolve falls back to key when missing`() {
        val locale = mockk<LocaleManager>()
        every { locale.string("missing.key") } returns null
        val service = MessageService(locale) { PluginConfig() }

        assertEquals("missing.key", service.resolve("missing.key"))
    }
}
