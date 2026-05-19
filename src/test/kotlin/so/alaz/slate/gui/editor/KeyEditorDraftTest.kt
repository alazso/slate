package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.KeyProvider

class KeyEditorDraftTest {
    @Test
    fun `writeToYaml writes key fields and clears nullable values`() {
        val yaml = YamlConfiguration()
        val draft = KeyEditorDraft(
            name = "<yellow>Example Key</yellow>",
            lore = listOf("<gray>Line 1</gray>"),
            material = Material.TRIPWIRE_HOOK,
            customModelData = emptyList(),
            customItemId = "",
            provider = KeyProvider.Vanilla,
            providerItemId = "",
            glowing = true,
        )

        draft.writeToYaml(yaml)

        assertEquals("<yellow>Example Key</yellow>", yaml.getString("key.name"))
        assertEquals(listOf("<gray>Line 1</gray>"), yaml.getStringList("key.lore"))
        assertEquals("TRIPWIRE_HOOK", yaml.getString("key.material"))
        assertEquals("vanilla", yaml.getString("key.provider"))
        assertEquals(true, yaml.getBoolean("key.glowing"))
        assertNull(yaml.get("key.providerItemId"))
        assertNull(yaml.get("key.customItemId"))
        assertNull(yaml.get("key.customModelData"))
    }
}
