package so.alaz.slate.gui.editor

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import so.alaz.slate.command.sub.DisplayMutationSupport
import so.alaz.slate.crate.CrateDefinition

internal data class DisplayEditorDraft(
    val state: DisplayMutationSupport.DisplayState,
    var dirty: Boolean,
) {
    companion object {
        fun fromCrate(crate: CrateDefinition): DisplayEditorDraft =
            DisplayEditorDraft(
                state = DisplayMutationSupport.fromDisplay(crate.display),
                dirty = false,
            )
    }

    fun setEnabled(value: Boolean) {
        state.enabled = value
        dirty = true
    }

    fun setViewRange(value: Float): DisplayMutationSupport.MutationResult {
        val result = DisplayMutationSupport.setViewRange(state, value)
        if (result.success) dirty = true
        return result
    }

    fun setBillboard(value: Display.Billboard) {
        state.billboard = value
        dirty = true
    }

    fun setLineSpacing(value: Double): DisplayMutationSupport.MutationResult {
        val result = DisplayMutationSupport.setLineSpacing(state, value)
        if (result.success) dirty = true
        return result
    }

    fun setLines(lines: List<String>) {
        state.lines.clear()
        state.lines.addAll(lines)
        dirty = true
    }

    fun setItemEnabled(value: Boolean) {
        state.item.enabled = value
        dirty = true
    }

    fun setItemMaterial(material: org.bukkit.Material) {
        state.item.material = material
        dirty = true
    }

    fun setItemHeightOffset(value: Double) {
        state.item.heightOffset = value
        dirty = true
    }

    fun setItemScale(value: Float): DisplayMutationSupport.MutationResult {
        val result = DisplayMutationSupport.setItemScale(state, value)
        if (result.success) dirty = true
        return result
    }

    fun setItemTransform(value: ItemDisplay.ItemDisplayTransform) {
        state.item.transform = value
        dirty = true
    }

    fun writeToYaml(yaml: YamlConfiguration) {
        DisplayMutationSupport.writeToYaml(state, yaml)
    }
}
