package so.alaz.slate.config

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(
    private val plugin: JavaPlugin,
    private val validator: ConfigValidator = ConfigValidator(),
) {
    private val configRef = AtomicReference(PluginConfig())
    val current: PluginConfig get() = configRef.get()

    fun load(): ValidationResult {
        plugin.saveDefaultConfig()
        val file = File(plugin.dataFolder, "config.yml")
        val yaml = YamlConfiguration.loadConfiguration(file)
        val result = validator.validate(file, yaml)
        if (result.isValid) {
            configRef.set(parsePluginConfig(yaml))
        }
        return result
    }

    fun reload(): ValidationResult = load()

    private fun parsePluginConfig(yaml: YamlConfiguration): PluginConfig {
        val dbType = when (yaml.getString("database.type", "sqlite")!!.lowercase()) {
            "postgres" -> DatabaseType.POSTGRES
            else -> DatabaseType.SQLITE
        }

        return PluginConfig(
            configVersion = yaml.getInt("config-version", 1),
            debug = yaml.getBoolean("debug", false),
            language = yaml.getString("language", "en").orEmpty(),
            broadcastPrefix = yaml.getString("broadcastPrefix", "<gold>[Slate]</gold> ").orEmpty(),
            crateScanRecursive = yaml.getBoolean("crateScanRecursive", false),
            maxBulkOpenAmount = yaml.getInt("maxBulkOpenAmount", 10),
            gui = GuiConfig(
                clickCooldownMs = yaml.getLong("gui.click-cooldown-ms", 100L),
                animationTimeoutSeconds = yaml.getLong("gui.animation-timeout-seconds", 30L),
                animationCloseSafeguard = AnimationCloseSafeguard.parse(
                    yaml.getString("gui.animation-close-safeguard"),
                ) ?: AnimationCloseSafeguard.COMPLETE_ON_CLOSE,
                closeOnDamage = yaml.getBoolean("gui.close-on-damage", true),
                menu = MenuGuiConfig(
                    rows = yaml.getInt("gui.menu.rows", 4),
                    borderMaterial = yaml.getString("gui.menu.border-material", "GRAY_STAINED_GLASS_PANE").orEmpty(),
                    crateSlots = SlotRangeParser.parse(yaml.getString("gui.menu.crate-slots"))
                        .ifEmpty { MenuGuiConfig().crateSlots },
                ),
            ),
            anchors = AnchorProtectionConfig(
                breakConfirmEnabled = yaml.getBoolean("anchors.break-confirmation.enabled", false),
                breakConfirmWindowSeconds = yaml.getLong("anchors.break-confirmation.window-seconds", 5L).coerceAtLeast(1L),
            ),
            database = DatabaseConfig(
                type = dbType,
                host = yaml.getString("database.host", "localhost").orEmpty(),
                port = yaml.getInt("database.port", 5432),
                name = yaml.getString("database.name", "slate").orEmpty(),
                user = yaml.getString("database.user", "slate").orEmpty(),
                password = yaml.getString("database.password", "").orEmpty(),
                poolSize = yaml.getInt("database.poolSize", 10),
                connectionTimeout = yaml.getLong("database.connectionTimeoutMs", 30_000L).milliseconds,
            ),
            cluster = ClusterConfig(
                redisEnabled = yaml.getBoolean("cluster.redis.enabled", false),
                redisHost = yaml.getString("cluster.redis.host", "localhost").orEmpty(),
                redisPort = yaml.getInt("cluster.redis.port", 6379),
                redisPassword = yaml.getString("cluster.redis.password", "").orEmpty(),
                redisDatabase = yaml.getInt("cluster.redis.database", 0),
                redisLockTtl = yaml.getLong("cluster.redis.lockTtlMs", 5_000L).milliseconds.takeIf { it.isPositive() } ?: 5.seconds,
                redisAcquireTimeout = yaml.getLong("cluster.redis.acquireTimeoutMs", 5_000L).milliseconds.takeIf { it.isPositive() } ?: 5.seconds,
            ),
            currency = parseCurrencyConfig(yaml),
        )
    }

    private fun parseCurrencyConfig(yaml: YamlConfiguration): CurrencyConfig {
        return CurrencyConfig(
            defaultProvider = yaml.getString("currency.default-provider", "auto").orEmpty(),
            defaultCurrency = yaml.getString("currency.default-currency", "coins").orEmpty(),
            ledgerEnabled = yaml.getBoolean("currency.ledger.enabled", true),
            vaultEnabled = yaml.getBoolean("currency.vault.enabled", true),
            vaultCurrencyId = yaml.getString("currency.vault.currency-id", "money").orEmpty(),
            vaultCurrencyName = yaml.getString("currency.vault.currency-name", "Money").orEmpty(),
            vaultCurrencySymbol = yaml.getString("currency.vault.currency-symbol"),
            vaultDecimals = yaml.getInt("currency.vault.decimals", 2),
            format = CurrencyFormatConfig(
                standard = yaml.getString("currency.format.standard", "us").orEmpty(),
                symbolPlacement = yaml.getString("currency.format.symbol-placement", "prefix").orEmpty(),
                useGrouping = yaml.getBoolean("currency.format.use-grouping", true),
                minFractionDigits = yaml.getInt("currency.format.min-fraction-digits", 2),
                maxFractionDigits = yaml.getInt("currency.format.max-fraction-digits", 2),
                defaultSymbol = yaml.getString("currency.format.default-symbol", "$").orEmpty(),
                customPattern = yaml.getString("currency.format.custom.pattern"),
                customDecimalSeparator = yaml.getString("currency.format.custom.decimal-separator", ".").orEmpty(),
                customGroupingSeparator = yaml.getString("currency.format.custom.grouping-separator", ",").orEmpty(),
            ),
        )
    }
}
