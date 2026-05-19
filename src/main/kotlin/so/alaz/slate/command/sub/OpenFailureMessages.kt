package so.alaz.slate.command.sub

import so.alaz.slate.api.SlateAPI
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.FailureReason
import so.alaz.slate.opening.OpeningResult

internal object OpenFailureMessages {
    fun reason(failure: OpeningResult.Failure): String {
        val placeholders = mapOf(
            "required" to failure.requiredKeys.orEmptyAmount(),
            "available" to failure.availableKeys.orEmptyAmount(),
        )
        val key = failure.reason.messageKey()
        return SlateAPI.messageService()?.resolve(key, placeholders) ?: failure.fallbackText()
    }

    fun reason(reason: FailureReason): String {
        val key = reason.messageKey()
        return SlateAPI.messageService()?.resolve(key) ?: reason.fallbackText()
    }

    fun stoppedSuffix(failure: OpeningResult.Failure): String {
        val renderedReason = reason(failure)
        return SlateAPI.messageService()?.resolve(
            MessageKeys.OPEN_STOPPED_SUFFIX,
            mapOf("reason" to renderedReason),
        ) ?: " - Stopped: $renderedReason"
    }

    fun stoppedSuffix(reason: FailureReason): String {
        val renderedReason = reason(reason)
        return SlateAPI.messageService()?.resolve(
            MessageKeys.OPEN_STOPPED_SUFFIX,
            mapOf("reason" to renderedReason),
        ) ?: " - Stopped: $renderedReason"
    }

    private fun FailureReason.messageKey(): String = when (this) {
        FailureReason.NO_KEY -> MessageKeys.OPEN_FAILED_REASON_NO_KEY
        FailureReason.INSUFFICIENT_KEYS -> MessageKeys.OPEN_FAILED_REASON_INSUFFICIENT_KEYS
        FailureReason.ON_COOLDOWN -> MessageKeys.OPEN_FAILED_REASON_ON_COOLDOWN
        FailureReason.MAX_PULLS_REACHED -> MessageKeys.OPEN_FAILED_REASON_MAX_PULLS_REACHED
        FailureReason.NOT_ALLOWED_WORLD -> MessageKeys.OPEN_FAILED_REASON_NOT_ALLOWED_WORLD
        FailureReason.CRATE_DISABLED -> MessageKeys.OPEN_FAILED_REASON_CRATE_DISABLED
        FailureReason.CRATE_ARCHIVED -> MessageKeys.OPEN_FAILED_REASON_CRATE_ARCHIVED
        FailureReason.CRATE_OUT_OF_SEASON -> MessageKeys.OPEN_FAILED_REASON_CRATE_OUT_OF_SEASON
        FailureReason.MISSING_PERMISSION -> MessageKeys.OPEN_FAILED_REASON_MISSING_PERMISSION
        FailureReason.REGION_RESTRICTED -> MessageKeys.OPEN_FAILED_REASON_REGION_RESTRICTED
        FailureReason.WORLDGUARD_UNAVAILABLE -> MessageKeys.OPEN_FAILED_REASON_WORLDGUARD_UNAVAILABLE
        FailureReason.INSUFFICIENT_CURRENCY -> MessageKeys.OPEN_FAILED_REASON_INSUFFICIENT_CURRENCY
        FailureReason.CANCELLED_BY_EVENT -> MessageKeys.OPEN_FAILED_REASON_CANCELLED_BY_EVENT
        FailureReason.DELIVERY_FAILED -> MessageKeys.OPEN_FAILED_REASON_DELIVERY_FAILED
    }

    private fun FailureReason.fallbackText(): String = when (this) {
        FailureReason.NO_KEY -> "You need the correct key for this crate."
        FailureReason.INSUFFICIENT_KEYS -> "This crate requires more keys than you have."
        FailureReason.ON_COOLDOWN -> "This crate is still on cooldown."
        FailureReason.MAX_PULLS_REACHED -> "You have reached the open limit for this crate."
        FailureReason.NOT_ALLOWED_WORLD -> "You can't open this crate in this world."
        FailureReason.CRATE_DISABLED -> "This crate is currently disabled."
        FailureReason.CRATE_ARCHIVED -> "This crate is archived and cannot be opened."
        FailureReason.CRATE_OUT_OF_SEASON -> "This crate is currently out of season."
        FailureReason.MISSING_PERMISSION -> "You are missing permission to open this crate."
        FailureReason.REGION_RESTRICTED -> "You can't open this crate in this region."
        FailureReason.WORLDGUARD_UNAVAILABLE -> "Region checks are enabled but WorldGuard is unavailable."
        FailureReason.INSUFFICIENT_CURRENCY -> "You don't have enough currency for this crate."
        FailureReason.CANCELLED_BY_EVENT -> "The crate opening was cancelled."
        FailureReason.DELIVERY_FAILED -> "The reward could not be delivered. It has been queued for recovery."
    }

    private fun OpeningResult.Failure.fallbackText(): String =
        if (reason == FailureReason.INSUFFICIENT_KEYS && requiredKeys != null && availableKeys != null) {
            "This crate requires $requiredKeys key(s), but you only have $availableKeys."
        } else {
            reason.fallbackText()
        }

    private fun Int?.orEmptyAmount(): String = this?.toString() ?: "0"
}
