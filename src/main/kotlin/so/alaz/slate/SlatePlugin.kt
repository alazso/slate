package so.alaz.slate

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.net.URI
import kotlin.time.Duration.Companion.seconds
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
import so.alaz.slate.key.KeyServiceImpl
import so.alaz.slate.listener.CrateInteractListener
import so.alaz.slate.message.LocaleManager
import so.alaz.slate.message.MessageService
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
    private var redisClient: JedisPooled? = null

    override fun onLoad() {
        SlateKeys.init(this)
    }

    override fun onEnable() {
        val scheduler = PlatformScheduler(this)
        mainDispatcher = BukkitMainDispatcher(scheduler)
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
        val openingPipeline = OpeningPipeline(keyService, prizeEngine, pityTracker, storageService, scheduler, crateEligibilityService)
        openService = OpenService(crateManager, keyService, openingPipeline, clusterCoordinator)
        anchorService = AnchorService(storageService)
        runBlocking {
            withTimeout(10.seconds) {
                anchorService.warmCache()
            }
        }

        val reloadSummary = crateManager.load()
        LogUtil.info("Loaded ${reloadSummary.cratesLoaded} crates (${reloadSummary.cratesFailed} failed)")
        displayService = DisplayService(scheduler, crateManager, anchorService, DisplayFactory(), pluginScope)
        displayService.cleanupAllStaleDisplays()
        displayService.reloadDisplays()
        guiService = GuiService(this, scheduler, pluginScope) { configManager.current }
        guiService.register()
        server.pluginManager.registerEvents(
            CrateInteractListener(pluginScope, keyService, openService, anchorService, scheduler),
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
        )
    }

    override fun onDisable() {
        if (this::displayService.isInitialized) {
            displayService.removeAllOwnedDisplays()
        }
        if (this::guiService.isInitialized) {
            guiService.closeAll()
        }
        runCatching {
            runBlocking(NonCancellable) {
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
        supervisorJob.cancel()
    }
}
