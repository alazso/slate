package so.alaz.slate.gui.editor

import org.bukkit.Material
import so.alaz.slate.crate.PrizeItem

internal object RewardEditorInputParser {
    private val rewardIdPattern = Regex("^[A-Za-z0-9_-]+$")

    fun parseRewardId(raw: String): String? {
        val normalized = raw.trim()
        if (normalized.isBlank()) return null
        if (!rewardIdPattern.matches(normalized)) return null
        return normalized
    }

    fun parseWeight(raw: String): Int? =
        raw.trim().toIntOrNull()?.takeIf { it > 0 }

    fun parseChance(raw: String): Double? =
        raw.trim().toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }

    fun parseBoolean(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "true", "yes", "y", "1", "on" -> true
        "false", "no", "n", "0", "off" -> false
        else -> null
    }

    fun parseLines(raw: String): List<String> =
        raw.split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    fun parsePrizeItem(raw: String): PrizeItem? {
        val tokens = raw.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val map = linkedMapOf<String, String>()
        tokens.forEach { token ->
            val idx = token.indexOf("=")
            if (idx <= 0) return null
            val key = token.substring(0, idx).trim().lowercase()
            val value = token.substring(idx + 1).trim()
            map[key] = value
        }

        val material = map["material"].orEmpty()
        val adapter = map["adapter"]?.takeIf { it.isNotBlank() }
        val itemId = map["itemid"]?.takeIf { it.isNotBlank() }
        val serialized = map["serialized"]?.takeIf { it.isNotBlank() }
        val amount = map["amount"]?.toIntOrNull() ?: 1
        val customModelData = map["cmd"]
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            .orEmpty()
        val pdc = parsePdcMap(map["pdc"])

        val hasMaterial = material.isNotBlank() && Material.matchMaterial(material) != null
        val hasSerialized = !serialized.isNullOrBlank()
        val hasAdapterPair = !adapter.isNullOrBlank() && !itemId.isNullOrBlank()
        if (!hasMaterial && !hasSerialized && !hasAdapterPair) {
            return null
        }
        if (amount <= 0) return null

        return PrizeItem(
            material = material,
            amount = amount,
            adapter = adapter,
            itemId = itemId,
            serialized = serialized,
            customModelData = customModelData,
            pdc = pdc,
        )
    }

    private fun parsePdcMap(raw: String?): Map<String, String> {
        val input = raw?.trim().orEmpty()
        if (input.isBlank()) return emptyMap()
        return input.split(",")
            .mapNotNull { pair ->
                val idx = pair.indexOf(":")
                if (idx <= 0) return@mapNotNull null
                val key = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (key.isBlank() || value.isBlank()) return@mapNotNull null
                key to value
            }
            .toMap()
    }
}
