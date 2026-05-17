package so.alaz.slate.display

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

enum class DisplayElementType {
    TEXT,
    ITEM,
}

data class DisplayElementSpec(
    val type: DisplayElementType,
    val offset: Vector = Vector(),
    val text: String? = null,
    val itemStack: ItemStack? = null,
    val scale: Float = 1.0f,
    val billboard: Display.Billboard = Display.Billboard.CENTER,
    val viewRange: Float = 32.0f,
    val alignment: TextDisplay.TextAlignment = TextDisplay.TextAlignment.CENTER,
    val shadowed: Boolean = true,
    val seeThrough: Boolean = false,
    val backgroundColor: Color? = null,
    val itemTransform: ItemDisplay.ItemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED,
)

data class DisplayGroupSpec(
    val ownerType: String,
    val ownerId: String,
    val baseLocation: org.bukkit.Location,
    val elements: List<DisplayElementSpec>,
)
