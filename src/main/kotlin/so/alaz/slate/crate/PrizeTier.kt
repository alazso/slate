package so.alaz.slate.crate

data class PrizeTier(
    val id: String,
    val displayName: String = id,
    val weight: Int = 1,
)
