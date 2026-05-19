package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KeyEditorInputParserTest {
    @Test
    fun `parse lore splits by pipe and trims`() {
        val parsed = KeyEditorInputParser.parseLore(" line one | line two |   ")
        assertEquals(listOf("line one", "line two"), parsed)
    }

    @Test
    fun `parse custom model data parses comma and space formats`() {
        assertEquals(listOf(1.0f, 2.5f), KeyEditorInputParser.parseCustomModelData("1, 2.5"))
        assertEquals(listOf(3.0f, 4.0f), KeyEditorInputParser.parseCustomModelData("3 4"))
    }

    @Test
    fun `parse custom model data rejects non numeric`() {
        assertNull(KeyEditorInputParser.parseCustomModelData("1, nope"))
    }

    @Test
    fun `parse material resolves case-insensitive`() {
        assertEquals(Material.TRIPWIRE_HOOK, KeyEditorInputParser.parseMaterial("tripwire_hook"))
    }
}
