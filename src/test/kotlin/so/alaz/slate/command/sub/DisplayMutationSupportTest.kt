package so.alaz.slate.command.sub

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.display.CrateDisplayConfig
import so.alaz.slate.display.CrateItemDisplayConfig

class DisplayMutationSupportTest {
    @Test
    fun `set view range rejects non-positive`() {
        val state = DisplayMutationSupport.fromDisplay(sampleDisplay())
        val result = DisplayMutationSupport.setViewRange(state, 0f)
        assertFalse(result.success)
        assertEquals(DisplayMutationSupport.Error.INVALID_VIEW_RANGE, result.error)
    }

    @Test
    fun `set item scale applies valid value`() {
        val state = DisplayMutationSupport.fromDisplay(sampleDisplay())
        val result = DisplayMutationSupport.setItemScale(state, 1.5f)
        assertTrue(result.success)
        assertEquals(1.5f, state.item.scale)
    }

    @Test
    fun `writeToYaml writes display keys`() {
        val state = DisplayMutationSupport.fromDisplay(sampleDisplay())
        state.enabled = true
        state.item.material = Material.BARREL
        state.lines.clear()
        state.lines.addAll(listOf("<gold>Crate</gold>", "<gray>Open me</gray>"))
        val yaml = YamlConfiguration()

        DisplayMutationSupport.writeToYaml(state, yaml)

        assertEquals(true, yaml.getBoolean("display.enabled"))
        assertEquals("barrel", yaml.getString("display.item.material")?.lowercase())
        assertEquals(2, yaml.getStringList("display.lines").size)
    }

    private fun sampleDisplay(): CrateDisplayConfig =
        CrateDisplayConfig(
            enabled = false,
            crateName = "Alpha",
            viewRange = 32f,
            billboard = Display.Billboard.CENTER,
            item = CrateItemDisplayConfig(
                enabled = true,
                material = Material.CHEST,
                heightOffset = 1.8,
                scale = 0.75f,
                transform = ItemDisplay.ItemDisplayTransform.FIXED,
            ),
            lineSpacing = 0.28,
            lines = listOf("<gold>Alpha</gold>"),
        )
}
