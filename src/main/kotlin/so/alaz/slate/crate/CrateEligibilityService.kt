package so.alaz.slate.crate

import java.time.Instant
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.opening.FailureReason
import so.alaz.slate.util.WorldRestrictionUtil

enum class CrateVisibility {
    VISIBLE,
    HIDDEN,
}

data class CrateOpenEligibility(
    val allowed: Boolean,
    val failureReason: FailureReason? = null,
)

class CrateEligibilityService(
    private val regionAccessService: RegionAccessService = NoopRegionAccessService,
    private val clock: () -> Instant = { Instant.now() },
) {
    fun visibilityFor(sender: CommandSender?, crate: CrateDefinition): CrateVisibility {
        if (crate.lifecycle.state == CrateState.ARCHIVED) return CrateVisibility.HIDDEN
        if (crate.lifecycle.state == CrateState.HIDDEN) return CrateVisibility.HIDDEN
        if (crate.lifecycle.state == CrateState.SEASONAL && !crate.lifecycle.isOpenInSeason(clock())) {
            return CrateVisibility.HIDDEN
        }
        if (sender != null && !hasRequiredPermissions(sender, crate.requirements.permissions.list)) {
            return CrateVisibility.HIDDEN
        }
        return CrateVisibility.VISIBLE
    }

    fun canPreview(sender: CommandSender, crate: CrateDefinition): CrateOpenEligibility {
        if (crate.lifecycle.state == CrateState.ARCHIVED) return CrateOpenEligibility(false, FailureReason.CRATE_ARCHIVED)
        if (crate.lifecycle.state == CrateState.SEASONAL && !crate.lifecycle.isOpenInSeason(clock())) {
            return CrateOpenEligibility(false, FailureReason.CRATE_OUT_OF_SEASON)
        }
        if (!hasRequiredPermissions(sender, crate.requirements.permissions.preview)) {
            return CrateOpenEligibility(false, FailureReason.MISSING_PERMISSION)
        }
        return CrateOpenEligibility(true)
    }

    fun canOpen(player: Player, crate: CrateDefinition): CrateOpenEligibility {
        if (crate.lifecycle.state == CrateState.ARCHIVED) return CrateOpenEligibility(false, FailureReason.CRATE_ARCHIVED)
        if (crate.lifecycle.state == CrateState.DISABLED) return CrateOpenEligibility(false, FailureReason.CRATE_DISABLED)
        if (crate.lifecycle.state == CrateState.SEASONAL && !crate.lifecycle.isOpenInSeason(clock())) {
            return CrateOpenEligibility(false, FailureReason.CRATE_OUT_OF_SEASON)
        }
        if (!player.hasPermission("${SlatePermissions.OPEN_CRATE_PREFIX}${crate.id}")) {
            return CrateOpenEligibility(false, FailureReason.MISSING_PERMISSION)
        }
        if (!hasRequiredPermissions(player, crate.requirements.permissions.open)) {
            return CrateOpenEligibility(false, FailureReason.MISSING_PERMISSION)
        }
        if (!WorldRestrictionUtil.isAllowed(player.world, crate.allowedWorlds)) {
            return CrateOpenEligibility(false, FailureReason.NOT_ALLOWED_WORLD)
        }
        val regionRequirements = crate.requirements.regions
        if (regionRequirements.allow.isNotEmpty() || regionRequirements.deny.isNotEmpty()) {
            val regions = regionAccessService.regionsAt(player)?.map(String::lowercase)?.toSet()
            if (regions == null) {
                return CrateOpenEligibility(false, FailureReason.WORLDGUARD_UNAVAILABLE)
            }
            if (regionRequirements.deny.map(String::lowercase).any { it in regions }) {
                return CrateOpenEligibility(false, FailureReason.REGION_RESTRICTED)
            }
            if (regionRequirements.allow.isNotEmpty() && regionRequirements.allow.map(String::lowercase).none { it in regions }) {
                return CrateOpenEligibility(false, FailureReason.REGION_RESTRICTED)
            }
        }
        return CrateOpenEligibility(true)
    }

    private fun hasRequiredPermissions(sender: CommandSender, required: Set<String>): Boolean =
        required.all { sender.hasPermission(it) }
}

