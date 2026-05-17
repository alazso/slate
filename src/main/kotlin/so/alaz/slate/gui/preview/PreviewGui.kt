package so.alaz.slate.gui.preview

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.gui.PaginatedGui
import so.alaz.slate.gui.StaticSlot
import so.alaz.slate.util.ComponentUtil

class PreviewGui(
    private val crate: CrateDefinition,
) : PaginatedGui(
    title = "Preview: ${so.alaz.slate.util.ComponentUtil.stripTags(crate.name)}",
    rows = crate.preview?.rows ?: 6,
    contentSlots = crate.preview?.contentSlots ?: listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
    prevSlot = crate.preview?.navPrevSlot ?: 48,
    nextSlot = crate.preview?.navNextSlot ?: 50,
    closeSlot = crate.preview?.navCloseSlot ?: 49,
    indicatorSlot = crate.preview?.infoSlot ?: 4,
) {
    private var filteredTier: String? = null
    private var sortMode: SortMode = SortMode.BY_WEIGHT

    init {
        slot(0, StaticSlot(ItemStack(Material.HOPPER)) {
            sortMode = sortMode.next()
        })
    }

    override suspend fun onBeforeOpen(player: Player) {
        applyBorder()
        super.onBeforeOpen(player)
        applyInfoSlot()
    }

    override suspend fun loadPage(player: Player, page: Int): List<ItemStack> {
        val pageSize = crate.preview?.contentSlots?.size ?: 21
        val sorted = when (sortMode) {
            SortMode.BY_WEIGHT -> crate.prizes.sortedByDescending { it.weight }
            SortMode.BY_TIER -> crate.prizes.sortedBy { it.tier.orEmpty() }
            SortMode.ALPHABETICAL -> crate.prizes.sortedBy { it.name.lowercase() }
        }
        val filtered = filteredTier?.let { tier -> sorted.filter { it.tier == tier } } ?: sorted
        val start = page * pageSize
        if (start >= filtered.size) return emptyList()
        return filtered
            .drop(start)
            .take(pageSize)
            .map { prize -> PreviewItemBuilder.build(crate, prize) }
    }

    private fun applyBorder() {
        val borderMaterial = crate.preview?.borderMaterial?.let(Material::matchMaterial) ?: Material.BLACK_STAINED_GLASS_PANE
        val filler = ItemStack(borderMaterial)
        val size = rows * 9
        (0 until 9).forEach { slot(it, StaticSlot(filler.clone())) }
        ((size - 9) until size).forEach { slot(it, StaticSlot(filler.clone())) }
        for (y in 1 until rows - 1) {
            slot((y * 9), StaticSlot(filler.clone()))
            slot((y * 9) + 8, StaticSlot(filler.clone()))
        }
    }

    private fun applyInfoSlot() {
        val info = ItemStack(Material.BOOK)
        info.editMeta { meta ->
            meta.displayName(ComponentUtil.parse("<gold>Preview</gold>"))
            meta.lore(
                listOf(
                    "<gray>Crate: ${ComponentUtil.stripTags(crate.name)}</gray>",
                    "<gray>Sort: ${sortMode.name.lowercase()}</gray>",
                    "<gray>Prizes: ${crate.prizes.size}</gray>",
                ).map(ComponentUtil::parse),
            )
        }
        slot(crate.preview?.infoSlot ?: 4, StaticSlot(info))
    }
}

private enum class SortMode {
    BY_WEIGHT,
    BY_TIER,
    ALPHABETICAL,
    ;

    fun next(): SortMode = when (this) {
        BY_WEIGHT -> BY_TIER
        BY_TIER -> ALPHABETICAL
        ALPHABETICAL -> BY_WEIGHT
    }
}

