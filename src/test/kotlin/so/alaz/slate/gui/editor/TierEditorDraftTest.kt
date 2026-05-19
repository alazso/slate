package so.alaz.slate.gui.editor

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.KeyDefinition
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.crate.Prize
import so.alaz.slate.crate.PrizeItem
import so.alaz.slate.crate.PrizeTier

class TierEditorDraftTest {
    @Test
    fun `renameSelectedTier rewrites reward references and marks draft dirty`() {
        val draft = TierEditorDraft.fromCrate(sampleCrate())
        draft.selectedTierId = "common"

        val result = draft.renameSelectedTier("starter")

        assertTrue(result.success)
        assertEquals("starter", draft.selectedTierId)
        assertTrue(draft.dirty)
        assertEquals("starter", draft.state.rewardTierAssignments["reward-a"])
    }

    @Test
    fun `deleteSelectedTier fails when rewards still reference tier`() {
        val draft = TierEditorDraft.fromCrate(sampleCrate())
        draft.selectedTierId = "common"

        val result = draft.deleteSelectedTier()

        assertFalse(result.success)
        assertEquals(listOf("reward-a"), result.referencedRewards)
    }

    @Test
    fun `writeToYaml persists tier and reward tier assignments`() {
        val draft = TierEditorDraft.fromCrate(sampleCrate())
        draft.selectedTierId = "rare"
        draft.setSelectedTierWeight(8)
        draft.setSelectedTierName("<gold>Rare+</gold>")

        val yaml = YamlConfiguration()
        yaml.createSection("prizes")
        yaml.createSection("prizes.reward-a")
        yaml.createSection("prizes.reward-b")

        draft.writeToYaml(yaml)

        assertEquals(8, yaml.getInt("tiers.rare.weight"))
        assertEquals("<gold>Rare+</gold>", yaml.getString("tiers.rare.name"))
        assertEquals("common", yaml.getString("prizes.reward-a.tier"))
        assertEquals("rare", yaml.getString("prizes.reward-b.tier"))
    }

    private fun sampleCrate(): CrateDefinition =
        CrateDefinition(
            id = "alpha",
            name = "Alpha",
            keyDefinition = KeyDefinition(
                name = "Alpha Key",
                lore = emptyList(),
                material = Material.TRIPWIRE_HOOK,
                customModelData = emptyList(),
                customItemId = null,
                provider = KeyProvider.Vanilla,
                providerItemId = null,
                glowing = false,
            ),
            tiers = mapOf(
                "common" to PrizeTier(id = "common", displayName = "Common", weight = 5),
                "rare" to PrizeTier(id = "rare", displayName = "Rare", weight = 2),
            ),
            prizes = listOf(
                Prize(
                    id = "reward-a",
                    name = "Reward A",
                    tier = "common",
                    weight = 1,
                    chance = null,
                    broadcast = false,
                    broadcastMessage = "",
                    items = listOf(PrizeItem(material = "DIAMOND")),
                    commands = emptyList(),
                    messages = emptyList(),
                ),
                Prize(
                    id = "reward-b",
                    name = "Reward B",
                    tier = "rare",
                    weight = 1,
                    chance = null,
                    broadcast = false,
                    broadcastMessage = "",
                    items = listOf(PrizeItem(material = "EMERALD")),
                    commands = emptyList(),
                    messages = emptyList(),
                ),
            ),
        )
}
