package so.alaz.slate.crate

import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.plugin.java.JavaPlugin
import so.alaz.slate.config.ConfigValidator
import so.alaz.slate.config.PluginConfig
import so.alaz.slate.config.SlotRangeParser
import so.alaz.slate.config.ValidationResult
import so.alaz.slate.display.CrateDisplayConfig
import so.alaz.slate.display.CrateItemDisplayConfig
import so.alaz.slate.key.providers.PhysicalKeyProviderRegistry
import so.alaz.slate.probability.SelectionDefinition
import so.alaz.slate.probability.SelectionMode
import so.alaz.slate.probability.SelectionStructure
import so.alaz.slate.util.LogUtil
import so.alaz.slate.util.parseSimpleDurationOrNull

class CrateManager(
    private val plugin: JavaPlugin,
    private val validator: ConfigValidator,
    private val pluginConfigProvider: () -> PluginConfig,
) {
    private val crates = ConcurrentHashMap<String, CrateDefinition>()
    private val lastKnownGoodByFile = ConcurrentHashMap<String, CrateDefinition>()

    fun byId(id: String): CrateDefinition? = crates[id]
    fun all(): Collection<CrateDefinition> = crates.values

    fun load(): ReloadSummary = reload()

    fun reload(): ReloadSummary {
        val cratesDir = File(plugin.dataFolder, "crates").apply { mkdirs() }
        val files = if (pluginConfigProvider().crateScanRecursive) {
            cratesDir.walkTopDown().filter { it.isFile && it.extension.equals("yml", true) }.toList()
        } else {
            cratesDir.listFiles { file -> file.isFile && file.extension.equals("yml", true) }?.toList().orEmpty()
        }

        var filesUpdated = 0
        var filesRetained = 0
        var filesFailed = 0
        val results = mutableMapOf<String, ValidationResult>()
        val fileOutcomes = mutableMapOf<String, ReloadFileOutcome>()
        val seenPaths = mutableSetOf<String>()
        val activeByFile = mutableMapOf<String, CrateDefinition>()

        for (file in files) {
            val filePath = file.path
            seenPaths += filePath
            val yaml = YamlConfiguration.loadConfiguration(file)
            val result = validator.validate(file, yaml)
            results[filePath] = result
            if (!result.isValid) {
                val retained = lastKnownGoodByFile[filePath]
                if (retained != null) {
                    activeByFile[filePath] = retained
                    filesRetained++
                    fileOutcomes[filePath] = ReloadFileOutcome(
                        status = ReloadFileStatus.RETAINED_LAST_GOOD,
                        crateId = retained.id,
                        details = result.errors.joinToString("; ") { it.message },
                    )
                } else {
                    filesFailed++
                    fileOutcomes[filePath] = ReloadFileOutcome(
                        status = ReloadFileStatus.FAILED,
                        details = result.errors.joinToString("; ") { it.message },
                    )
                }
                continue
            }

            runCatching { parseCrate(file, yaml) }
                .onSuccess {
                    lastKnownGoodByFile[filePath] = it
                    activeByFile[filePath] = it
                    filesUpdated++
                    fileOutcomes[filePath] = ReloadFileOutcome(
                        status = ReloadFileStatus.UPDATED,
                        crateId = it.id,
                    )
                }
                .onFailure { error ->
                    val retained = lastKnownGoodByFile[filePath]
                    if (retained != null) {
                        activeByFile[filePath] = retained
                        filesRetained++
                        fileOutcomes[filePath] = ReloadFileOutcome(
                            status = ReloadFileStatus.RETAINED_LAST_GOOD,
                            crateId = retained.id,
                            details = error.message,
                        )
                    } else {
                        filesFailed++
                        fileOutcomes[filePath] = ReloadFileOutcome(
                            status = ReloadFileStatus.FAILED,
                            details = error.message,
                        )
                    }
                }
        }

        val removedPaths = lastKnownGoodByFile.keys - seenPaths
        for (removed in removedPaths) {
            lastKnownGoodByFile.remove(removed)
            fileOutcomes[removed] = ReloadFileOutcome(
                status = ReloadFileStatus.REMOVED,
            )
        }

        crates.clear()
        activeByFile.values.forEach { crates[it.id] = it }
        val cratesFailed = filesFailed

        fileOutcomes.forEach { (path, outcome) ->
            when (outcome.status) {
                ReloadFileStatus.FAILED -> LogUtil.warn("Reload failed for $path :: ${outcome.details ?: "unknown error"}")
                ReloadFileStatus.RETAINED_LAST_GOOD -> LogUtil.warn("Reload retained last-known-good for $path :: ${outcome.details ?: "validation/parse failure"}")
                ReloadFileStatus.REMOVED -> LogUtil.info("Removed crate file from runtime: $path")
                ReloadFileStatus.UPDATED -> LogUtil.debug("Reload updated $path -> ${outcome.crateId}")
            }
        }

        return ReloadSummary(
            filesTotal = files.size,
            filesUpdated = filesUpdated,
            filesRetained = filesRetained,
            filesFailed = filesFailed,
            filesRemoved = removedPaths.size,
            cratesLoaded = crates.size,
            cratesFailed = cratesFailed,
            cratePerFileResults = results,
            fileOutcomes = fileOutcomes,
        )
    }

    private fun parseCrate(file: File, yaml: YamlConfiguration): CrateDefinition {
        val id = file.nameWithoutExtension
        val selection = SelectionDefinition(
            mode = SelectionMode.parse(yaml.getString("selection.mode")) ?: SelectionMode.WEIGHTED,
            structure = SelectionStructure.parse(yaml.getString("selection.structure")) ?: SelectionStructure.FLAT,
            allowEmpty = yaml.getBoolean("selection.allow-empty", false),
            emptyWeight = yaml.getInt("selection.empty-weight", 0),
        )
        val tiers = yaml.getConfigurationSection("tiers")
            ?.getKeys(false)
            ?.associateWith { tierId ->
                PrizeTier(
                    id = tierId,
                    displayName = yaml.getString("tiers.$tierId.name", tierId).orEmpty(),
                    weight = yaml.getInt("tiers.$tierId.weight", 1),
                )
            }
            .orEmpty()

        val prizeSection = yaml.getConfigurationSection("prizes")
            ?: error("Missing prizes section for $id")
        val prizes = prizeSection.getKeys(false).map { key ->
            val path = "prizes.$key"
            Prize(
                id = key,
                name = yaml.getString("$path.name", key).orEmpty(),
                tier = yaml.getString("$path.tier"),
                weight = yaml.getInt("$path.weight", 0),
                chance = (yaml.get("$path.chance") as? Number)?.toDouble(),
                broadcast = yaml.getBoolean("$path.broadcast", false),
                broadcastMessage = yaml.getString("$path.broadcastMessage", "").orEmpty(),
                items = parsePrizeItems(yaml.getList("$path.items")),
                commands = yaml.getStringList("$path.commands"),
                messages = yaml.getStringList("$path.messages"),
            )
        }

        val cmdRaw = yaml.get("key.customModelData")
        val customModelData = when (cmdRaw) {
            is Number -> listOf(cmdRaw.toFloat())
            is List<*> -> cmdRaw.mapNotNull { (it as? Number)?.toFloat() }
            else -> emptyList()
        }

        val keyMaterial = Material.matchMaterial(yaml.getString("key.material", "TRIPWIRE_HOOK").orEmpty())
            ?: Material.TRIPWIRE_HOOK
        val keyProvider = KeyProvider.parse(yaml.getString("key.provider")) ?: KeyProvider.Vanilla

        val crate = CrateDefinition(
            id = id,
            crateVersion = yaml.getInt("crate-version", 1),
            name = yaml.getString("name", id).orEmpty(),
            type = CrateType.Unknown,
            requiredKeys = yaml.getInt("requiredKeys", 1),
            maxPulls = yaml.getInt("maxPulls", -1),
            cooldown = yaml.getString("cooldown", "0s").orEmpty().parseSimpleDurationOrNull() ?: 0.seconds,
            allowedWorlds = normalizeWorlds(yaml.getStringList("allowedWorlds").toSet()),
            lifecycle = parseLifecycle(yaml),
            requirements = parseRequirements(yaml),
            animation = parseAnimationConfig(yaml),
            preview = parsePreviewConfig(yaml),
            pityConfig = yaml.getConfigurationSection("pity")?.let { pity ->
                PityConfig(
                    enabled = pity.getBoolean("enabled", false),
                    opensRequired = pity.getInt("opensRequired", 0),
                    guaranteedTier = pity.getString("guaranteedTier", "").orEmpty(),
                )
            },
            keyDefinition = KeyDefinition(
                name = yaml.getString("key.name", "<yellow>$id Key</yellow>").orEmpty(),
                lore = yaml.getStringList("key.lore"),
                material = keyMaterial,
                customModelData = customModelData,
                customItemId = yaml.getString("key.customItemId"),
                provider = keyProvider,
                providerItemId = yaml.getString("key.providerItemId"),
                glowing = yaml.getBoolean("key.glowing", false),
            ),
            prizes = prizes,
            tiers = tiers,
            selection = selection,
            display = parseDisplayConfig(yaml),
        )
        PhysicalKeyProviderRegistry.validate(crate)?.let { message ->
            error(message)
        }
        return crate
    }

    private fun parseLifecycle(yaml: YamlConfiguration): CrateLifecycleConfig {
        val state = CrateState.parse(yaml.getString("state")) ?: CrateState.ACTIVE
        val seasonalWindows = yaml.getMapList("seasonal.windows").mapNotNull { raw ->
            val startsRaw = raw["starts"]?.toString() ?: return@mapNotNull null
            val endsRaw = raw["ends"]?.toString() ?: return@mapNotNull null
            val starts = runCatching { Instant.parse(startsRaw) }.getOrNull() ?: return@mapNotNull null
            val ends = runCatching { Instant.parse(endsRaw) }.getOrNull() ?: return@mapNotNull null
            if (!ends.isAfter(starts)) return@mapNotNull null
            SeasonalWindow(startsAt = starts, endsAt = ends)
        }
        return CrateLifecycleConfig(
            state = state,
            seasonalWindows = seasonalWindows,
        )
    }

    private fun parseRequirements(yaml: YamlConfiguration): CrateRequirements =
        CrateRequirements(
            permissions = CratePermissionRequirements(
                open = yaml.getStringList("requirements.permissions.open").map(String::trim).filter { it.isNotBlank() }.toSet(),
                preview = yaml.getStringList("requirements.permissions.preview").map(String::trim).filter { it.isNotBlank() }.toSet(),
                list = yaml.getStringList("requirements.permissions.list").map(String::trim).filter { it.isNotBlank() }.toSet(),
            ),
            regions = CrateRegionRequirements(
                allow = yaml.getStringList("requirements.regions.allow").map(String::trim).filter { it.isNotBlank() }.toSet(),
                deny = yaml.getStringList("requirements.regions.deny").map(String::trim).filter { it.isNotBlank() }.toSet(),
                requireWorldGuard = yaml.getBoolean("requirements.regions.requireWorldGuard", false),
            ),
        )

    private fun parseAnimationConfig(yaml: YamlConfiguration): CrateAnimationConfig? {
        if (!yaml.contains("animation")) return null
        return CrateAnimationConfig(
            enabled = yaml.getBoolean("animation.enabled", false),
            template = yaml.getString("animation.template", "csgo").orEmpty(),
            tickInterval = yaml.getInt("animation.tick-interval", 1).coerceAtLeast(1),
            durationTicks = yaml.getInt("animation.duration-ticks", 80).coerceAtLeast(1),
            soundTick = yaml.getString("animation.sound-tick")?.takeIf { runCatching { Sound.valueOf(it) }.isSuccess },
            soundReveal = yaml.getString("animation.sound-reveal")?.takeIf { runCatching { Sound.valueOf(it) }.isSuccess },
            skipPermission = yaml.getString("animation.skip-permission", "slate.animation.skip").orEmpty(),
            decoyCount = yaml.getInt("animation.decoy-count", 20).coerceAtLeast(0),
        )
    }

    private fun parsePreviewConfig(yaml: YamlConfiguration): CratePreviewConfig? {
        if (!yaml.contains("preview")) return null
        val raw = yaml.getString("preview.content-slots")
        val parsedSlots = if (raw != null) SlotRangeParser.parse(raw) else yaml.getIntegerList("preview.content-slots")
        return CratePreviewConfig(
            enabled = yaml.getBoolean("preview.enabled", true),
            rows = yaml.getInt("preview.rows", 6),
            borderMaterial = yaml.getString("preview.border-material", "BLACK_STAINED_GLASS_PANE").orEmpty(),
            contentSlots = parsedSlots.ifEmpty { CratePreviewConfig().contentSlots },
            navPrevSlot = yaml.getInt("preview.nav-prev-slot", 48),
            navNextSlot = yaml.getInt("preview.nav-next-slot", 50),
            navCloseSlot = yaml.getInt("preview.nav-close-slot", 49),
            infoSlot = yaml.getInt("preview.info-slot", 4),
            showChances = yaml.getBoolean("preview.show-chances", true),
            showTiers = yaml.getBoolean("preview.show-tiers", true),
        )
    }

    private fun parseDisplayConfig(yaml: YamlConfiguration): CrateDisplayConfig {
        val itemMaterial = Material.matchMaterial(yaml.getString("display.item.material", "CHEST").orEmpty())
            ?: Material.CHEST
        return CrateDisplayConfig(
            enabled = yaml.getBoolean("display.enabled", false),
            crateName = yaml.getString("name")?.takeIf { it.isNotBlank() },
            viewRange = yaml.getDouble("display.view-range", 32.0).toFloat().coerceIn(1.0f, 128.0f),
            billboard = parseBillboard(yaml.getString("display.billboard")) ?: Display.Billboard.CENTER,
            item = CrateItemDisplayConfig(
                enabled = yaml.getBoolean("display.item.enabled", true),
                material = itemMaterial,
                heightOffset = yaml.getDouble("display.item.height-offset", 1.85),
                scale = yaml.getDouble("display.item.scale", 0.75).toFloat().coerceIn(0.05f, 8.0f),
                transform = parseItemTransform(yaml.getString("display.item.transform")) ?: ItemDisplay.ItemDisplayTransform.FIXED,
            ),
            lineSpacing = yaml.getDouble("display.line-spacing", 0.28).coerceIn(0.05, 2.0),
            lines = yaml.getStringList("display.lines"),
        )
    }

    private fun parseBillboard(raw: String?): Display.Billboard? = when (raw?.trim()?.lowercase()) {
        null, "" -> null
        "fixed" -> Display.Billboard.FIXED
        "vertical" -> Display.Billboard.VERTICAL
        "horizontal" -> Display.Billboard.HORIZONTAL
        "center", "centre" -> Display.Billboard.CENTER
        else -> null
    }

    private fun parseItemTransform(raw: String?): ItemDisplay.ItemDisplayTransform? =
        ItemDisplay.ItemDisplayTransform.entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

    private fun normalizeWorlds(input: Set<String>): Set<String> {
        if (input.isEmpty()) return emptySet()
        return input.flatMap { world ->
            val normalized = world.trim()
            if (normalized.isBlank()) return@flatMap emptySet<String>()
            val namespaced = if (":" in normalized) normalized else "minecraft:$normalized"
            when (normalized.lowercase()) {
                "world", "minecraft:world", "minecraft:overworld" -> setOf("world", "minecraft:world", "minecraft:overworld")
                "world_nether", "minecraft:world_nether", "minecraft:the_nether" -> setOf("world_nether", "minecraft:world_nether", "minecraft:the_nether")
                "world_the_end", "minecraft:world_the_end", "minecraft:the_end" -> setOf("world_the_end", "minecraft:world_the_end", "minecraft:the_end")
                else -> setOf(normalized, namespaced)
            }
        }.toSet()
    }

    private fun parsePrizeItems(raw: List<*>?): List<PrizeItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.mapNotNull { entry ->
            when (entry) {
                is String -> PrizeItem(material = entry, amount = 1)
                is Map<*, *> -> {
                    val material = (entry["material"] as? String).orEmpty()
                    val amount = (entry["amount"] as? Number)?.toInt() ?: 1
                    val adapter = entry["adapter"] as? String
                    val itemId = entry["itemId"] as? String
                    val serialized = entry["serialized"] as? String
                    val customModelData = (entry["customModelData"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() }.orEmpty()
                    val pdcRaw = entry["pdc"] as? Map<*, *>
                    val pdc = pdcRaw?.entries
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v?.toString() ?: return@mapNotNull null
                            key to value
                        }
                        ?.toMap()
                        .orEmpty()
                    PrizeItem(
                        material = material,
                        amount = amount,
                        adapter = adapter,
                        itemId = itemId,
                        serialized = serialized,
                        customModelData = customModelData,
                        pdc = pdc,
                    )
                }

                else -> null
            }
        }
    }
}
