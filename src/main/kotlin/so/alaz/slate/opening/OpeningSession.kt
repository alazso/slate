package so.alaz.slate.opening

import org.bukkit.entity.Player
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.Prize
import so.alaz.slate.key.KeyType

data class OpeningSession(
    val player: Player,
    val crate: CrateDefinition,
    val keyType: KeyType,
    var keyConsumed: Boolean = false, // Mutable: this state flips as the pipeline advances.
    var selectedPrize: Prize? = null, // Mutable: set once selection completes.
)
