package so.alaz.slate.config

data class ValidationResult(
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class ValidationError(
    val filePath: String,
    val keyPath: String,
    val message: String,
    val suggestion: String? = null,
)

data class ValidationWarning(
    val filePath: String,
    val keyPath: String,
    val message: String,
)
