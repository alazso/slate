package so.alaz.slate.config

import java.io.File
import java.time.Instant
import kotlin.math.abs
import org.bukkit.NamespacedKey
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ItemDisplay
import org.bukkit.Sound
import so.alaz.slate.crate.KeyProvider
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure
import so.alaz.slate.util.parseSimpleDurationOrNull

class ConfigValidator {
    fun validate(file: File, config: YamlConfiguration): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        if (file.name == "config.yml") {
            validateRootConfig(file, config, errors)
            return ValidationResult(errors, warnings)
        }

        val expectedId = file.nameWithoutExtension
        val configuredId = config.getString("id")
        if (configuredId != null && configuredId != expectedId) {
            warnings += ValidationWarning(file.path, "id", "id does not match filename; filename will be used")
        }

        val crateVersion = config.getInt("crate-version", 1)
        if (crateVersion < 1) {
            errors += ValidationError(file.path, "crate-version", "crate-version must be >= 1")
        }

        val state = config.getString("state", "active")
        if (state !in setOf("active", "disabled", "hidden", "archived", "seasonal")) {
            errors += ValidationError(file.path, "state", "state must be active, disabled, hidden, archived, or seasonal")
        }
        validateSeasonalFields(file, config, state, errors)

        if (config.getString("name").isNullOrBlank()) {
            errors += ValidationError(file.path, "name", "name must not be blank")
        }

        val language = config.getString("language")
        if (language != null && language.isBlank()) {
            errors += ValidationError(file.path, "language", "language must not be blank")
        }

        val requiredKeys = config.getInt("requiredKeys", 1)
        if (requiredKeys < 1) errors += ValidationError(file.path, "requiredKeys", "requiredKeys must be >= 1")

        val maxPulls = config.getInt("maxPulls", -1)
        if (maxPulls < -1) errors += ValidationError(file.path, "maxPulls", "maxPulls must be -1 or greater")

        val cooldownRaw = config.getString("cooldown", "0s").orEmpty()
        val cooldown = cooldownRaw.parseSimpleDurationOrNull()
        if (cooldown == null) {
            errors += ValidationError(file.path, "cooldown", "cooldown must be a valid duration")
        } else if (cooldown.inWholeMilliseconds < 0) {
            errors += ValidationError(file.path, "cooldown", "cooldown cannot be negative")
        }

        val mode = SelectionMode.parse(config.getString("selection.mode"))
        if (mode == null) {
            errors += ValidationError(file.path, "selection.mode", "selection.mode must be weighted or percent")
        }

        val structure = SelectionStructure.parse(config.getString("selection.structure"))
        if (structure == null) {
            errors += ValidationError(file.path, "selection.structure", "selection.structure must be flat or tiers")
        }

        val allowEmpty = config.getBoolean("selection.allow-empty", false)
        val emptyWeight = config.getInt("selection.empty-weight", 0)
        if (emptyWeight < 0) {
            errors += ValidationError(file.path, "selection.empty-weight", "selection.empty-weight must be >= 0")
        }

        val prizesSection = config.getConfigurationSection("prizes")
        val prizeKeys = prizesSection?.getKeys(false).orEmpty()
        if (prizesSection == null || prizeKeys.isEmpty()) {
            errors += ValidationError(file.path, "prizes", "at least one prize is required")
        } else {
            var totalPercent = 0.0
            var totalWeighted = 0
            prizeKeys.forEach { key ->
                val path = "prizes.$key"
                val hasWeight = config.contains("$path.weight")
                val hasChance = config.contains("$path.chance")
                val weight = config.getInt("$path.weight", 0)
                val chance = (config.get("$path.chance") as? Number)?.toDouble()

                if (hasWeight && hasChance) {
                    errors += ValidationError(file.path, path, "prize cannot define both weight and chance")
                }

                when (mode) {
                    SelectionMode.WEIGHTED -> {
                        if (!hasWeight) {
                            errors += ValidationError(file.path, "$path.weight", "weight is required in weighted mode")
                        } else if (weight <= 0) {
                            errors += ValidationError(file.path, "$path.weight", "weight must be > 0 in weighted mode")
                        }
                        if (hasChance) {
                            errors += ValidationError(file.path, "$path.chance", "chance is not allowed in weighted mode")
                        }
                        totalWeighted += weight.coerceAtLeast(0)
                    }

                    SelectionMode.PERCENT -> {
                        if (!hasChance || chance == null) {
                            errors += ValidationError(file.path, "$path.chance", "chance is required in percent mode")
                        } else {
                            if (chance <= 0.0) {
                                errors += ValidationError(file.path, "$path.chance", "chance must be > 0 in percent mode")
                            }
                            if (chance > 100.0) {
                                errors += ValidationError(file.path, "$path.chance", "chance must be <= 100")
                            }
                            totalPercent += chance.coerceAtLeast(0.0)
                        }
                        if (hasWeight) {
                            errors += ValidationError(file.path, "$path.weight", "weight is not allowed in percent mode")
                        }
                    }

                    null -> Unit
                }

                val itemsRaw = config.getList("$path.items").orEmpty()
                val itemErrors = validatePrizeItems(file.path, path, itemsRaw)
                errors += itemErrors
                val commands = config.getStringList("$path.commands")
                val messages = config.getStringList("$path.messages")
                if (itemsRaw.isEmpty() && commands.isEmpty() && messages.isEmpty()) {
                    errors += ValidationError(file.path, path, "prize must define at least one of items/commands/messages")
                }
                if (config.contains("$path.minRange") || config.contains("$path.maxRange")) {
                    errors += ValidationError(file.path, path, "minRange/maxRange is not supported")
                }
            }

            if (mode == SelectionMode.PERCENT) {
                val epsilon = 1.0e-6
                if (!allowEmpty && abs(totalPercent - 100.0) > epsilon) {
                    errors += ValidationError(
                        file.path,
                        "prizes",
                        "percent mode total chance must equal 100 (actual=$totalPercent)",
                    )
                }
                if (allowEmpty && totalPercent > 100.0 + epsilon) {
                    errors += ValidationError(
                        file.path,
                        "prizes",
                        "percent mode total chance cannot exceed 100 when allow-empty is enabled (actual=$totalPercent)",
                    )
                }
            }

            if (mode == SelectionMode.WEIGHTED && totalWeighted <= 0) {
                errors += ValidationError(file.path, "prizes", "weighted mode requires total weight > 0")
            }
        }

        val tierIds = config.getConfigurationSection("tiers")?.getKeys(false).orEmpty()
        if (structure == SelectionStructure.TIERS) {
            if (mode != SelectionMode.WEIGHTED) {
                errors += ValidationError(file.path, "selection.structure", "tiers structure is only supported for weighted mode")
            }
            if (allowEmpty) {
                errors += ValidationError(file.path, "selection.allow-empty", "allow-empty is not supported for tiered weighted mode")
            }
            if (tierIds.isEmpty()) {
                errors += ValidationError(file.path, "tiers", "tiers structure requires at least one tier")
            }

            tierIds.forEach { tierId ->
                val tierWeight = config.getInt("tiers.$tierId.weight", 0)
                if (tierWeight <= 0) {
                    errors += ValidationError(file.path, "tiers.$tierId.weight", "tier weight must be > 0")
                }
            }

            val rewardsByTier = mutableMapOf<String, Int>()
            prizeKeys.forEach { key ->
                val tier = config.getString("prizes.$key.tier")
                if (tier.isNullOrBlank()) {
                    errors += ValidationError(file.path, "prizes.$key.tier", "tier is required for tiered weighted structure")
                } else {
                    if (tier !in tierIds) {
                        errors += ValidationError(file.path, "prizes.$key.tier", "unknown tier '$tier'")
                    }
                    rewardsByTier[tier] = (rewardsByTier[tier] ?: 0) + 1
                }
            }
            tierIds.forEach { tierId ->
                if ((rewardsByTier[tierId] ?: 0) <= 0) {
                    errors += ValidationError(file.path, "tiers.$tierId", "tier '$tierId' has no rewards")
                }
            }
        }

        val keyMaterial = config.getString("key.material")
        if (keyMaterial.isNullOrBlank() || Material.matchMaterial(keyMaterial) == null) {
            errors += ValidationError(file.path, "key.material", "invalid material")
        }

        validateKeyStrictFields(file, config, errors, warnings)
        validateRequirementFields(file, config, errors, warnings)
        validateGuiFields(file, config, errors, warnings)
        validateDisplayFields(file, config, errors, warnings)

        if (config.contains("type")) {
            warnings += ValidationWarning(file.path, "type", "type is reserved in foundation and currently maps to Unknown")
        }

        return ValidationResult(errors, warnings)
    }

    private fun validateRootConfig(
        file: File,
        config: YamlConfiguration,
        errors: MutableList<ValidationError>,
    ) {
        val maxBulkOpenAmount = config.getInt("maxBulkOpenAmount", 10)
        if (maxBulkOpenAmount < 1) {
            errors += ValidationError(file.path, "maxBulkOpenAmount", "maxBulkOpenAmount must be >= 1")
        }

        val configVersion = config.getInt("config-version", 1)
        if (configVersion < 1) {
            errors += ValidationError(file.path, "config-version", "config-version must be >= 1")
        }

        val dbType = config.getString("database.type", "sqlite")!!.lowercase()
        if (dbType !in setOf("sqlite", "postgres")) {
            errors += ValidationError(file.path, "database.type", "database.type must be sqlite or postgres")
        }

        if (dbType == "postgres") {
            if (config.getString("database.host").isNullOrBlank()) {
                errors += ValidationError(file.path, "database.host", "database.host is required for postgres")
            }
            if (config.getString("database.name").isNullOrBlank()) {
                errors += ValidationError(file.path, "database.name", "database.name is required for postgres")
            }
            if (config.getString("database.user").isNullOrBlank()) {
                errors += ValidationError(file.path, "database.user", "database.user is required for postgres")
            }
        }

        if (config.getBoolean("cluster.redis.enabled", false)) {
            if (config.getString("cluster.redis.host").isNullOrBlank()) {
                errors += ValidationError(file.path, "cluster.redis.host", "cluster.redis.host is required when redis is enabled")
            }
            if (config.getInt("cluster.redis.port", 6379) !in 1..65535) {
                errors += ValidationError(file.path, "cluster.redis.port", "cluster.redis.port must be between 1 and 65535")
            }
            if (config.getInt("cluster.redis.database", 0) < 0) {
                errors += ValidationError(file.path, "cluster.redis.database", "cluster.redis.database must be >= 0")
            }
            if (config.getLong("cluster.redis.lockTtlMs", 5_000L) <= 0) {
                errors += ValidationError(file.path, "cluster.redis.lockTtlMs", "cluster.redis.lockTtlMs must be > 0")
            }
            if (config.getLong("cluster.redis.acquireTimeoutMs", 5_000L) <= 0) {
                errors += ValidationError(file.path, "cluster.redis.acquireTimeoutMs", "cluster.redis.acquireTimeoutMs must be > 0")
            }
        }

        val clickCooldownMs = config.getLong("gui.click-cooldown-ms", 100L)
        if (clickCooldownMs < 0L) {
            errors += ValidationError(file.path, "gui.click-cooldown-ms", "gui.click-cooldown-ms must be >= 0")
        }
        val animationTimeoutSeconds = config.getLong("gui.animation-timeout-seconds", 30L)
        if (animationTimeoutSeconds <= 0L) {
            errors += ValidationError(file.path, "gui.animation-timeout-seconds", "gui.animation-timeout-seconds must be > 0")
        }
        val closeSafeguard = config.getString("gui.animation-close-safeguard")
        if (closeSafeguard != null && AnimationCloseSafeguard.parse(closeSafeguard) == null) {
            errors += ValidationError(
                file.path,
                "gui.animation-close-safeguard",
                "gui.animation-close-safeguard must be complete-on-close or prevent-close-until-complete",
            )
        }
        val menuRows = config.getInt("gui.menu.rows", 4)
        if (menuRows !in 1..6) {
            errors += ValidationError(file.path, "gui.menu.rows", "gui.menu.rows must be between 1 and 6")
        }
        val menuBorder = config.getString("gui.menu.border-material")
        if (!menuBorder.isNullOrBlank() && Material.matchMaterial(menuBorder) == null) {
            errors += ValidationError(file.path, "gui.menu.border-material", "invalid material")
        }
        val crateSlots = SlotRangeParser.parse(config.getString("gui.menu.crate-slots"))
        val maxSlot = (menuRows * 9) - 1
        if (crateSlots.any { it !in 0..maxSlot }) {
            errors += ValidationError(file.path, "gui.menu.crate-slots", "all gui.menu.crate-slots must be between 0 and $maxSlot")
        }
        if (crateSlots.size != crateSlots.toSet().size) {
            errors += ValidationError(file.path, "gui.menu.crate-slots", "gui.menu.crate-slots must be unique")
        }
    }

    private fun validateKeyStrictFields(
        file: File,
        config: YamlConfiguration,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
    ) {
        val providerRaw = config.getString("key.provider")
        val provider = KeyProvider.parse(providerRaw)
        if (provider == null) {
            errors += ValidationError(
                file.path,
                "key.provider",
                "key.provider must be one of: vanilla, nexo, craftengine",
            )
        }
        val providerItemId = config.getString("key.providerItemId")?.trim().orEmpty()
        if (provider != null && provider != KeyProvider.Vanilla && providerItemId.isBlank()) {
            errors += ValidationError(
                file.path,
                "key.providerItemId",
                "key.providerItemId is required when key.provider is '$provider'",
            )
        }
        if (provider == KeyProvider.Vanilla && providerItemId.isNotBlank()) {
            warnings += ValidationWarning(
                file.path,
                "key.providerItemId",
                "key.providerItemId is ignored when key.provider is vanilla",
            )
        }

        if (config.contains("key.customModelData")) {
            val raw = config.get("key.customModelData")
            when (raw) {
                is Number -> {
                    val value = raw.toFloat()
                    if (!value.isFinite()) {
                        errors += ValidationError(file.path, "key.customModelData", "customModelData must be finite numbers")
                    }
                }

                is List<*> -> {
                    if (raw.isEmpty()) {
                        warnings += ValidationWarning(file.path, "key.customModelData", "customModelData list is empty; strict key matching may reject keys without explicit model data")
                    }
                    raw.forEachIndexed { index, value ->
                        val number = value as? Number
                        if (number == null || !number.toFloat().isFinite()) {
                            errors += ValidationError(
                                file.path,
                                "key.customModelData[$index]",
                                "customModelData entries must be finite numbers",
                            )
                        }
                    }
                }

                else -> {
                    errors += ValidationError(
                        file.path,
                        "key.customModelData",
                        "customModelData must be a number or list of numbers",
                    )
                }
            }
        }

        val customItemIdRaw = config.getString("key.customItemId")?.trim().orEmpty()
        if (customItemIdRaw.isNotBlank() && NamespacedKey.fromString(customItemIdRaw) == null) {
            warnings += ValidationWarning(
                file.path,
                "key.customItemId",
                "customItemId should be a valid namespaced key (e.g. namespace:item_id); strict key matching may reject malformed ids",
            )
        }
    }

    private fun validatePrizeItems(filePath: String, prizePath: String, itemsRaw: List<*>): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        itemsRaw.forEachIndexed { index, entry ->
            val path = "$prizePath.items[$index]"
            when (entry) {
                is String -> {
                    if (entry.isBlank()) {
                        errors += ValidationError(filePath, path, "item string must not be blank")
                    }
                }

                is Map<*, *> -> {
                    val material = (entry["material"] as? String).orEmpty()
                    val adapter = (entry["adapter"] as? String).orEmpty()
                    val itemId = (entry["itemId"] as? String).orEmpty()
                    val serialized = (entry["serialized"] as? String).orEmpty()
                    val amount = (entry["amount"] as? Number)?.toInt() ?: 1
                    if (amount <= 0) {
                        errors += ValidationError(filePath, "$path.amount", "amount must be > 0")
                    }
                    if (material.isBlank() && serialized.isBlank() && (adapter.isBlank() || itemId.isBlank())) {
                        errors += ValidationError(
                            filePath,
                            path,
                            "item map requires serialized, material, or adapter+itemId",
                        )
                    }
                    if (material.isNotBlank() && Material.matchMaterial(material) == null) {
                        errors += ValidationError(filePath, "$path.material", "invalid material '$material'")
                    }
                }

                else -> {
                    errors += ValidationError(filePath, path, "item must be string or map")
                }
            }
        }
        return errors
    }

    private fun validateSeasonalFields(
        file: File,
        config: YamlConfiguration,
        state: String?,
        errors: MutableList<ValidationError>,
    ) {
        if (!state.equals("seasonal", ignoreCase = true)) return
        val windows = config.getMapList("seasonal.windows")
        if (windows.isEmpty()) {
            errors += ValidationError(file.path, "seasonal.windows", "seasonal crates must define at least one seasonal window")
            return
        }
        windows.forEachIndexed { index, raw ->
            val path = "seasonal.windows[$index]"
            val startsRaw = raw["starts"]?.toString()
            val endsRaw = raw["ends"]?.toString()
            val starts = runCatching { startsRaw?.let(Instant::parse) }.getOrNull()
            val ends = runCatching { endsRaw?.let(Instant::parse) }.getOrNull()
            if (starts == null) {
                errors += ValidationError(file.path, "$path.starts", "starts must be an ISO-8601 instant")
            }
            if (ends == null) {
                errors += ValidationError(file.path, "$path.ends", "ends must be an ISO-8601 instant")
            }
            if (starts != null && ends != null && !ends.isAfter(starts)) {
                errors += ValidationError(file.path, path, "seasonal window end must be after start")
            }
        }
    }

    private fun validateRequirementFields(
        file: File,
        config: YamlConfiguration,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
    ) {
        validatePermissionList(file, config.getStringList("requirements.permissions.open"), "requirements.permissions.open", errors)
        validatePermissionList(file, config.getStringList("requirements.permissions.preview"), "requirements.permissions.preview", errors)
        validatePermissionList(file, config.getStringList("requirements.permissions.list"), "requirements.permissions.list", errors)

        val allow = config.getStringList("requirements.regions.allow")
        val deny = config.getStringList("requirements.regions.deny")
        val blankAllow = allow.any { it.isBlank() }
        val blankDeny = deny.any { it.isBlank() }
        if (blankAllow) {
            errors += ValidationError(file.path, "requirements.regions.allow", "region ids in allow must not be blank")
        }
        if (blankDeny) {
            errors += ValidationError(file.path, "requirements.regions.deny", "region ids in deny must not be blank")
        }
        val overlap = allow.map(String::lowercase).toSet().intersect(deny.map(String::lowercase).toSet())
        if (overlap.isNotEmpty()) {
            warnings += ValidationWarning(file.path, "requirements.regions", "allow/deny overlap for regions: ${overlap.joinToString(", ")}")
        }
    }

    private fun validatePermissionList(
        file: File,
        values: List<String>,
        path: String,
        errors: MutableList<ValidationError>,
    ) {
        if (values.any { it.isBlank() }) {
            errors += ValidationError(file.path, path, "permission entries must not be blank")
        }
    }

    private fun validateDisplayFields(
        file: File,
        config: YamlConfiguration,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
    ) {
        if (!config.contains("display")) return

        val viewRange = config.getDouble("display.view-range", 32.0)
        if (viewRange <= 0.0) {
            errors += ValidationError(file.path, "display.view-range", "view-range must be greater than 0")
        }

        val billboard = config.getString("display.billboard")
        if (!billboard.isNullOrBlank() && billboard.lowercase() !in setOf("fixed", "vertical", "horizontal", "center", "centre")) {
            warnings += ValidationWarning(file.path, "display.billboard", "unknown billboard mode; center will be used")
        }

        val itemMaterial = config.getString("display.item.material")
        if (!itemMaterial.isNullOrBlank() && Material.matchMaterial(itemMaterial) == null) {
            warnings += ValidationWarning(file.path, "display.item.material", "invalid material; CHEST will be used")
        }

        val itemScale = config.getDouble("display.item.scale", 0.75)
        if (itemScale <= 0.0) {
            errors += ValidationError(file.path, "display.item.scale", "item scale must be greater than 0")
        }

        val transform = config.getString("display.item.transform")
        if (
            !transform.isNullOrBlank() &&
            ItemDisplay.ItemDisplayTransform.entries.none { it.name.equals(transform, ignoreCase = true) }
        ) {
            warnings += ValidationWarning(file.path, "display.item.transform", "unknown item transform; fixed will be used")
        }
    }

    private fun validateGuiFields(
        file: File,
        config: YamlConfiguration,
        errors: MutableList<ValidationError>,
        warnings: MutableList<ValidationWarning>,
    ) {
        if (config.getBoolean("animation.enabled", false)) {
            val template = config.getString("animation.template", "csgo").orEmpty()
            if (template !in setOf("csgo", "slot-machine", "agency-pick")) {
                errors += ValidationError(file.path, "animation.template", "animation.template must be csgo, slot-machine, or agency-pick")
            }
            val tickInterval = config.getInt("animation.tick-interval", 1)
            if (tickInterval <= 0) {
                errors += ValidationError(file.path, "animation.tick-interval", "animation.tick-interval must be > 0")
            }
            val durationTicks = config.getInt("animation.duration-ticks", 80)
            if (durationTicks <= 0) {
                errors += ValidationError(file.path, "animation.duration-ticks", "animation.duration-ticks must be > 0")
            }
            val decoyCount = config.getInt("animation.decoy-count", 20)
            if (decoyCount < 0) {
                errors += ValidationError(file.path, "animation.decoy-count", "animation.decoy-count must be >= 0")
            }
            val skipPermission = config.getString("animation.skip-permission")
            if (skipPermission != null && skipPermission.isBlank()) {
                errors += ValidationError(file.path, "animation.skip-permission", "animation.skip-permission must not be blank")
            }
            validateSound(file, config.getString("animation.sound-tick"), "animation.sound-tick", warnings)
            validateSound(file, config.getString("animation.sound-reveal"), "animation.sound-reveal", warnings)
        }

        if (config.contains("preview")) {
            val rows = config.getInt("preview.rows", 6)
            if (rows !in 1..6) {
                errors += ValidationError(file.path, "preview.rows", "preview.rows must be between 1 and 6")
            }
            val borderMaterial = config.getString("preview.border-material")
            if (!borderMaterial.isNullOrBlank() && Material.matchMaterial(borderMaterial) == null) {
                errors += ValidationError(file.path, "preview.border-material", "invalid material")
            }
            val rawSlots = config.getString("preview.content-slots")
            val contentSlots = if (rawSlots != null) SlotRangeParser.parse(rawSlots) else config.getIntegerList("preview.content-slots")
            val maxSlot = (rows * 9) - 1
            if (contentSlots.isEmpty()) {
                errors += ValidationError(file.path, "preview.content-slots", "preview.content-slots must define at least one slot")
            }
            if (contentSlots.any { it !in 0..maxSlot }) {
                errors += ValidationError(file.path, "preview.content-slots", "all preview.content-slots must be between 0 and $maxSlot")
            }
            if (contentSlots.size != contentSlots.toSet().size) {
                errors += ValidationError(file.path, "preview.content-slots", "preview.content-slots must be unique")
            }
            val navSlots = listOf(
                "preview.nav-prev-slot" to config.getInt("preview.nav-prev-slot", 48),
                "preview.nav-next-slot" to config.getInt("preview.nav-next-slot", 50),
                "preview.nav-close-slot" to config.getInt("preview.nav-close-slot", 49),
                "preview.info-slot" to config.getInt("preview.info-slot", 4),
            )
            navSlots.forEach { (path, slot) ->
                if (slot !in 0..maxSlot) {
                    errors += ValidationError(file.path, path, "$path must be between 0 and $maxSlot")
                }
            }
        }
    }

    private fun validateSound(
        file: File,
        raw: String?,
        path: String,
        warnings: MutableList<ValidationWarning>,
    ) {
        if (raw.isNullOrBlank()) return
        if (runCatching { Sound.valueOf(raw) }.isFailure) {
            warnings += ValidationWarning(file.path, path, "unknown sound '$raw'; sound will be ignored")
        }
    }
}
