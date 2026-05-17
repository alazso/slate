package so.alaz.slate.crate

data class CrateAnimationConfig(
    val enabled: Boolean = false,
    val template: String = "csgo",
    val tickInterval: Int = 1,
    val durationTicks: Int = 80,
    val soundTick: String? = null,
    val soundReveal: String? = null,
    val skipPermission: String = "slate.animation.skip",
    val decoyCount: Int = 20,
)

data class CratePreviewConfig(
    val enabled: Boolean = true,
    val rows: Int = 6,
    val borderMaterial: String = "BLACK_STAINED_GLASS_PANE",
    val contentSlots: List<Int> = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
    val navPrevSlot: Int = 48,
    val navNextSlot: Int = 50,
    val navCloseSlot: Int = 49,
    val infoSlot: Int = 4,
    val showChances: Boolean = true,
    val showTiers: Boolean = true,
)

