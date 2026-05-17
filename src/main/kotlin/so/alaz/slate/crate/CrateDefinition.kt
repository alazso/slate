package so.alaz.slate.crate

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import so.alaz.slate.display.CrateDisplayConfig
import so.alaz.slate.probability.SelectionDefinition

data class CrateDefinition(
    val id: String,
    val crateVersion: Int = 1,
    val name: String,
    val type: CrateType = CrateType.Unknown,
    val requiredKeys: Int = 1,
    val maxPulls: Int = -1,
    val cooldown: Duration = ZERO,
    val allowedWorlds: Set<String> = emptySet(),
    val lifecycle: CrateLifecycleConfig = CrateLifecycleConfig(),
    val requirements: CrateRequirements = CrateRequirements(),
    val animation: CrateAnimationConfig? = null,
    val preview: CratePreviewConfig? = null,
    val pityConfig: PityConfig? = null,
    val keyDefinition: KeyDefinition,
    val prizes: List<Prize>,
    val tiers: Map<String, PrizeTier> = emptyMap(),
    val selection: SelectionDefinition = SelectionDefinition(),
    val display: CrateDisplayConfig = CrateDisplayConfig(),
)

data class PityConfig(
    val enabled: Boolean,
    val opensRequired: Int,
    val guaranteedTier: String,
)
