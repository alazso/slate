package so.alaz.slate.command.sub

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import so.alaz.slate.display.CrateDisplayConfig

internal object DisplayMutationSupport {
    data class DisplayState(
        var enabled: Boolean,
        var viewRange: Float,
        var billboard: Display.Billboard,
        var lineSpacing: Double,
        val lines: MutableList<String>,
        val item: ItemState,
    )

    data class ItemState(
        var enabled: Boolean,
        var material: Material,
        var heightOffset: Double,
        var scale: Float,
        var transform: ItemDisplay.ItemDisplayTransform,
    )

    enum class Error {
        INVALID_VIEW_RANGE,
        INVALID_ITEM_SCALE,
        INVALID_LINE_SPACING,
    }

    data class MutationResult(
        val success: Boolean,
        val error: Error? = null,
    )

    fun fromDisplay(config: CrateDisplayConfig): DisplayState =
        DisplayState(
            enabled = config.enabled,
            viewRange = config.viewRange,
            billboard = config.billboard,
            lineSpacing = config.lineSpacing,
            lines = config.lines.toMutableList(),
            item = ItemState(
                enabled = config.item.enabled,
                material = config.item.material,
                heightOffset = config.item.heightOffset,
                scale = config.item.scale,
                transform = config.item.transform,
            ),
        )

    fun setViewRange(state: DisplayState, value: Float): MutationResult {
        if (value <= 0f) return MutationResult(false, Error.INVALID_VIEW_RANGE)
        state.viewRange = value.coerceIn(1.0f, 128.0f)
        return MutationResult(true)
    }

    fun setLineSpacing(state: DisplayState, value: Double): MutationResult {
        if (value <= 0.0) return MutationResult(false, Error.INVALID_LINE_SPACING)
        state.lineSpacing = value.coerceIn(0.05, 2.0)
        return MutationResult(true)
    }

    fun setItemScale(state: DisplayState, value: Float): MutationResult {
        if (value <= 0f) return MutationResult(false, Error.INVALID_ITEM_SCALE)
        state.item.scale = value.coerceIn(0.05f, 8.0f)
        return MutationResult(true)
    }

    fun writeToYaml(state: DisplayState, yaml: YamlConfiguration) {
        yaml.set("display.enabled", state.enabled)
        yaml.set("display.view-range", state.viewRange.toDouble())
        yaml.set("display.billboard", state.billboard.name.lowercase())
        yaml.set("display.line-spacing", state.lineSpacing)
        yaml.set("display.lines", state.lines)

        yaml.set("display.item.enabled", state.item.enabled)
        yaml.set("display.item.material", state.item.material.name)
        yaml.set("display.item.height-offset", state.item.heightOffset)
        yaml.set("display.item.scale", state.item.scale.toDouble())
        yaml.set("display.item.transform", state.item.transform.name)
    }
}
