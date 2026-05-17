package so.alaz.slate.config

data class GuiConfig(
    val clickCooldownMs: Long = 100L,
    val animationTimeoutSeconds: Long = 30L,
    val animationCloseSafeguard: AnimationCloseSafeguard = AnimationCloseSafeguard.COMPLETE_ON_CLOSE,
    val closeOnDamage: Boolean = true,
    val menu: MenuGuiConfig = MenuGuiConfig(),
)

data class MenuGuiConfig(
    val rows: Int = 4,
    val borderMaterial: String = "GRAY_STAINED_GLASS_PANE",
    val crateSlots: List<Int> = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
)

enum class AnimationCloseSafeguard {
    COMPLETE_ON_CLOSE,
    PREVENT_CLOSE_UNTIL_COMPLETE,
    ;

    companion object {
        fun parse(raw: String?): AnimationCloseSafeguard? = when (raw?.trim()?.lowercase()) {
            "complete-on-close", "complete_on_close", "instant-complete" -> COMPLETE_ON_CLOSE
            "prevent-close-until-complete", "prevent_close_until_complete", "lock-until-complete" -> PREVENT_CLOSE_UNTIL_COMPLETE
            else -> null
        }
    }
}

