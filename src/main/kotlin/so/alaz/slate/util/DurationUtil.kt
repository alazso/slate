package so.alaz.slate.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun String.parseSimpleDurationOrNull(): Duration? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val number = trimmed.dropLast(1).toLongOrNull() ?: return null
    return when (trimmed.last().lowercaseChar()) {
        's' -> number.seconds
        'm' -> number.minutes
        'h' -> number.hours
        else -> null
    }
}
