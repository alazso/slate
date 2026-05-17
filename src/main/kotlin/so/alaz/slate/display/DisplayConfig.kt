package so.alaz.slate.display

import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay

data class CrateDisplayConfig(
    val enabled: Boolean = false,
    val crateName: String? = null,
    val viewRange: Float = 32.0f,
    val billboard: Display.Billboard = Display.Billboard.CENTER,
    val item: CrateItemDisplayConfig = CrateItemDisplayConfig(),
    val lineSpacing: Double = 0.28,
    val lines: List<String> = emptyList(),
)

data class CrateItemDisplayConfig(
    val enabled: Boolean = true,
    val material: Material = Material.CHEST,
    val heightOffset: Double = 1.85,
    val scale: Float = 0.75f,
    val transform: ItemDisplay.ItemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED,
)
