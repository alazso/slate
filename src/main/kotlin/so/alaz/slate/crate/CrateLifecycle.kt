package so.alaz.slate.crate

import java.time.Instant

enum class CrateState {
    ACTIVE,
    DISABLED,
    HIDDEN,
    ARCHIVED,
    SEASONAL,
    ;

    companion object {
        fun parse(raw: String?): CrateState? = when (raw?.trim()?.lowercase()) {
            null, "", "active" -> ACTIVE
            "disabled" -> DISABLED
            "hidden" -> HIDDEN
            "archived" -> ARCHIVED
            "seasonal" -> SEASONAL
            else -> null
        }
    }
}

data class SeasonalWindow(
    val startsAt: Instant,
    val endsAt: Instant,
) {
    fun isActive(now: Instant): Boolean = !now.isBefore(startsAt) && now.isBefore(endsAt)
}

data class CrateLifecycleConfig(
    val state: CrateState = CrateState.ACTIVE,
    val seasonalWindows: List<SeasonalWindow> = emptyList(),
) {
    fun isOpenInSeason(now: Instant): Boolean {
        if (state != CrateState.SEASONAL) return true
        return seasonalWindows.any { it.isActive(now) }
    }
}

data class CratePermissionRequirements(
    val open: Set<String> = emptySet(),
    val preview: Set<String> = emptySet(),
    val list: Set<String> = emptySet(),
)

data class CrateRegionRequirements(
    val allow: Set<String> = emptySet(),
    val deny: Set<String> = emptySet(),
    val requireWorldGuard: Boolean = false,
)

data class CrateRequirements(
    val permissions: CratePermissionRequirements = CratePermissionRequirements(),
    val regions: CrateRegionRequirements = CrateRegionRequirements(),
)

