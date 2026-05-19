package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.entity.Display
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DisplayEditorInputParserTest {
    @Test
    fun `parse material resolves valid input`() {
        assertEquals(Material.CHEST, DisplayEditorInputParser.parseMaterial("chest"))
        assertNull(DisplayEditorInputParser.parseMaterial("bad_mat"))
    }

    @Test
    fun `parse billboard resolves known values`() {
        assertEquals(Display.Billboard.CENTER, DisplayEditorInputParser.parseBillboard("center"))
        assertEquals(Display.Billboard.FIXED, DisplayEditorInputParser.parseBillboard("fixed"))
        assertNull(DisplayEditorInputParser.parseBillboard("bad"))
    }

    @Test
    fun `parse lines splits by pipe`() {
        assertEquals(listOf("one", "two"), DisplayEditorInputParser.parseLines(" one | two | "))
    }
}
