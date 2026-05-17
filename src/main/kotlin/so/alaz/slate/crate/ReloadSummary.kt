package so.alaz.slate.crate

import so.alaz.slate.config.ValidationResult

data class ReloadSummary(
    val filesTotal: Int,
    val filesUpdated: Int,
    val filesRetained: Int,
    val filesFailed: Int,
    val filesRemoved: Int,
    val cratesLoaded: Int,
    val cratesFailed: Int,
    val cratePerFileResults: Map<String, ValidationResult>,
    val fileOutcomes: Map<String, ReloadFileOutcome>,
) {
    val isSuccess: Boolean get() = cratesFailed == 0
}

data class ReloadFileOutcome(
    val status: ReloadFileStatus,
    val crateId: String? = null,
    val details: String? = null,
)

enum class ReloadFileStatus {
    UPDATED,
    RETAINED_LAST_GOOD,
    FAILED,
    REMOVED,
}
