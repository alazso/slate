package so.alaz.slate

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.Duration
import java.net.URI
import kotlin.time.Duration.Companion.seconds
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import redis.clients.jedis.JedisPooled
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.config.ConfigManager
import so.alaz.slate.config.ConfigValidator
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.crate.WorldGuardRegionAccessService
import so.alaz.slate.display.DisplayFactory
import so.alaz.slate.display.DisplayService
import so.alaz.slate.economy.EconomyProviderResolver
import so.alaz.slate.economy.EconomyCurrencyFormatter
import so.alaz.slate.economy.SlateEconomyService
import so.alaz.slate.key.KeyServiceImpl
import so.alaz.slate.listener.CrateInteractListener
import so.alaz.slate.listener.DisplayLifecycleListener
import so.alaz.slate.message.LocaleManager
import so.alaz.slate.message.MessageService
import so.alaz.slate.player.ClaimService
import so.alaz.slate.player.PlayerCrateQueryService
import so.alaz.slate.gui.GuiService
import so.alaz.slate.opening.OpeningPipeline
import so.alaz.slate.opening.OpenService
import so.alaz.slate.prize.PrizeEngineImpl
import so.alaz.slate.prize.StorageBackedPityTracker
import so.alaz.slate.scheduler.BukkitMainDispatcher
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.scheduler.ServerPlatform
import so.alaz.slate.storage.DatabaseFactory
import so.alaz.slate.storage.ExposedStorage
import so.alaz.slate.storage.NoopClusterCoordinator
import so.alaz.slate.storage.RedisClusterCoordinator
import so.alaz.slate.util.LogUtil

class SlatePlugin : JavaPlugin() {
    private val supervisorJob = SupervisorJob()
    val pluginScope: CoroutineScope = CoroutineScope(supervisorJob + Dispatchers.IO + CoroutineName("Slate"))
    lateinit var mainDispatcher: BukkitMainDispatcher
        private set
    lateinit var platformScheduler: PlatformScheduler
        private set

    lateinit var configManager: ConfigManager
        private set
    lateinit var crateManager: CrateManager
        private set
    lateinit var crateEligibilityService: CrateEligibilityService
        private set
    lateinit var storageService: ExposedStorage
        private set
    lateinit var keyService: KeyServiceImpl
        private set
    lateinit var openService: OpenService
        private set
    lateinit var anchorService: AnchorService
        private set
    lateinit var displayService: DisplayService
        private set
    lateinit var guiService: GuiService
        private set
    lateinit var localeManager: LocaleManager
        private set
    lateinit var messageService: MessageService
        private set
    lateinit var economyService: SlateEconomyService
        private set
    lateinit var claimService: ClaimService
        private set
    lateinit var playerCrateQueryService: PlayerCrateQueryService
        private set
    private var redisClient: JedisPooled? = null
    private var placeholderExpansionHandle: Any? = null

    override fun onLoad() {
        SlateKeys.init(this)
    }

    override fun onEnable() {
        platformScheduler = PlatformScheduler(this)
        mainDispatcher = BukkitMainDispatcher(platformScheduler)
        configManager = ConfigManager(this, ConfigValidator())
        localeManager = LocaleManager(this)

        val configResult = configManager.load()
        LogUtil.init(this) { configManager.current.debug }
        LogUtil.info("Running on ${ServerPlatform.current}")
        if (!configResult.isValid) {
            configResult.errors.forEach(LogUtil::validationError)
            server.pluginManager.disablePlugin(this)
            return
        }
        localeManager.load(configManager.current.language)
        messageService = MessageService(localeManager) { configManager.current }

        val db = DatabaseFactory.create(dataFolder, configManager.current.database)
        storageService = ExposedStorage(db.dataSource, db.dialect)
        runBlocking {
            withTimeout(30.seconds) {
                storageService.initialize()
            }
        }
        val activeEconomyProvider = EconomyProviderResolver(server, configManager.current.currency).resolve()
        val currencyFormatter = EconomyCurrencyFormatter(configManager.current.currency.format)
        economyService = SlateEconomyService(
            activeProvider = activeEconomyProvider,
            defaultProviderId = activeEconomyProvider.id,
            defaultCurrencyId = configManager.current.currency.defaultCurrency,
            formatter = currencyFormatter,
        )
        LogUtil.info(
            "Economy provider selected: ${activeEconomyProvider.id} " +
                "(available=${activeEconomyProvider.isAvailable()}, " +
                "multiCurrency=${activeEconomyProvider.capabilities().supportsMultipleCurrencies})",
        )
        server.servicesManager.register(
            SlateEconomyService::class.java,
            economyService,
            this,
            ServicePriority.Normal,
        )

        crateManager = CrateManager(this, ConfigValidator()) { configManager.current }
        crateEligibilityService = CrateEligibilityService(WorldGuardRegionAccessService())
        keyService = KeyServiceImpl(crateManager, storageService, mainDispatcher)
        val prizeEngine = PrizeEngineImpl()
        val pityTracker = StorageBackedPityTracker(storageService)
        val clusterCoordinator = if (configManager.current.cluster.redisEnabled) {
            val cfg = configManager.current.cluster
            val auth = if (cfg.redisPassword.isBlank()) "" else ":${cfg.redisPassword}@"
            val client = JedisPooled(
                URI.create("redis://$auth${cfg.redisHost}:${cfg.redisPort}/${cfg.redisDatabase}"),
            )
            redisClient = client
            runCatching {
                client.ping()
            }.onFailure { error ->
                LogUtil.warn("Redis setup warning: ${error.message}")
            }
            LogUtil.info("Cluster coordination enabled with Redis")
            RedisClusterCoordinator(
                jedis = client,
                lockTtl = Duration.ofMillis(cfg.redisLockTtl.inWholeMilliseconds),
                acquireTimeout = Duration.ofMillis(cfg.redisAcquireTimeout.inWholeMilliseconds),
            )
        } else {
            NoopClusterCoordinator
        }
        val openingPipeline = OpeningPipeline(
            keyService,
            prizeEngine,
            pityTracker,
            storageService,
            platformScheduler,
            crateEligibilityService,
            economyService,
        )
        openService = OpenService(crateManager, keyService, openingPipeline, clusterCoordinator)
        anchorService = AnchorService(storageService)
        claimService = ClaimService(storageService, crateManager, platformScheduler)
        playerCrateQueryService = PlayerCrateQueryService(
            storage = storageService,
            crateManager = crateManager,
            keyService = keyService,
            anchorService = anchorService,
            claimService = claimService,
        )
        runBlocking {
            withTimeout(10.seconds) {
                anchorService.warmCache()
            }
        }
        seedDefaultCrateIfEmpty()

        val reloadSummary = crateManager.load()
        LogUtil.info("Loaded ${reloadSummary.cratesLoaded} crates (${reloadSummary.cratesFailed} failed)")
        displayService = DisplayService(platformScheduler, crateManager, anchorService, DisplayFactory(), pluginScope)
        displayService.cleanupAllStaleDisplays()
        displayService.reloadDisplays()
        guiService = GuiService(this, platformScheduler, pluginScope) { configManager.current }
        guiService.register()
        server.pluginManager.registerEvents(
            CrateInteractListener(pluginScope, keyService, openService, anchorService, platformScheduler) { configManager.current },
            this,
        )
        server.pluginManager.registerEvents(
            DisplayLifecycleListener(displayService),
            this,
        )

        SlateAPI.init(
            this,
            configManager,
            crateManager,
            crateEligibilityService,
            keyService,
            openService,
            storageService,
            anchorService,
            displayService,
            guiService,
            pluginScope,
            messageService,
            economyService,
            claimService,
            playerCrateQueryService,
        )

        registerPlaceholderExpansion()
    }

    override fun onDisable() {
        unregisterPlaceholderExpansion()
        if (this::displayService.isInitialized) {
            displayService.removeAllOwnedDisplaysImmediate()
        }
        if (this::guiService.isInitialized) {
            guiService.closeAll()
        }
        runCatching {
            runBlocking {
                withTimeout(10.seconds) {
                    if (this@SlatePlugin::storageService.isInitialized) {
                        storageService.shutdown()
                    }
                    redisClient?.close()
                }
            }
        }.onFailure {
            LogUtil.error("Error during shutdown", it)
        }
        runCatching {
            supervisorJob.cancel()
        }.onFailure {
            LogUtil.warn("Coroutine scope cancellation warning: ${it.message}")
        }
    }

    private fun seedDefaultCrateIfEmpty() {
        val cratesDir = File(dataFolder, "crates").apply { mkdirs() }
        val hasAnyCrateYaml = cratesDir.walkTopDown()
            .any { it.isFile && it.extension.equals("yml", ignoreCase = true) }
        if (hasAnyCrateYaml) return
        val defaultCrates = listOf(
            "nebula_vault.yml",
            "inferno_cache.yml",
            "gilded_jackpot.yml",
            "deepsea_slots.yml",
            "shadow_contract.yml",
            "verdant_choice.yml",
        )
        defaultCrates.forEach { fileName ->
            saveResource("crates/$fileName", false)
        }
        LogUtil.info("Seeded ${defaultCrates.size} default crates into crates/")
    }

    private fun registerPlaceholderExpansion() {
        if (server.pluginManager.getPlugin("PlaceholderAPI") == null) {
            LogUtil.info("PlaceholderAPI not detected; %slate_*% placeholders disabled")
            return
        }
        val expansion = runCatching {
            val expansionClass = Class.forName("so.alaz.slate.placeholder.SlatePlaceholderExpansion")
            val constructor = expansionClass.getConstructor(SlatePlugin::class.java)
            constructor.newInstance(this)
        }.onFailure {
            LogUtil.warn("PlaceholderAPI detected but Slate expansion class failed to load: ${it.message}")
        }.getOrNull() ?: return

        val registered = runCatching {
            val registerMethod = expansion.javaClass.getMethod("register")
            registerMethod.invoke(expansion) as? Boolean ?: false
        }.onFailure {
            LogUtil.warn("PlaceholderAPI detected but Slate expansion registration failed: ${it.message}")
        }.getOrDefault(false)

        if (!registered) {
            LogUtil.warn("PlaceholderAPI detected but Slate expansion failed to register")
            return
        }

        placeholderExpansionHandle = expansion
        LogUtil.info("Registered PlaceholderAPI expansion: %slate_*%")
    }

    private fun unregisterPlaceholderExpansion() {
        val expansion = placeholderExpansionHandle ?: return
        runCatching {
            val unregisterMethod = expansion.javaClass.getMethod("unregister")
            unregisterMethod.invoke(expansion)
        }.onFailure {
            LogUtil.warn("Failed to unregister PlaceholderAPI expansion cleanly: ${it.message}")
        }
        placeholderExpansionHandle = null
    }
}
