package so.alaz.slate.api

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import org.bukkit.inventory.ItemStack
import so.alaz.slate.SlatePlugin
import so.alaz.slate.config.ConfigManager
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.CrateManager
import so.alaz.slate.display.DisplayService
import so.alaz.slate.economy.SlateEconomyService
import so.alaz.slate.gui.GuiService
import so.alaz.slate.key.KeyService
import so.alaz.slate.message.MessageService
import so.alaz.slate.opening.OpenService
import so.alaz.slate.player.ClaimService
import so.alaz.slate.player.PlayerCrateQueryService
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.StorageService

object SlateAPI {
    private var plugin: SlatePlugin? = null
    private var configManager: ConfigManager? = null
    private var crateManager: CrateManager? = null
    private var crateEligibilityService: CrateEligibilityService? = null
    private var keyService: KeyService? = null
    private var openService: OpenService? = null
    private var storageService: StorageService? = null
    private var anchorService: AnchorService? = null
    private var displayService: DisplayService? = null
    private var guiService: GuiService? = null
    private var pluginScope: CoroutineScope? = null
    private var messageService: MessageService? = null
    private var economyService: SlateEconomyService? = null
    private var claimService: ClaimService? = null
    private var playerCrateQueryService: PlayerCrateQueryService? = null

    fun init(
        plugin: SlatePlugin,
        configManager: ConfigManager,
        crateManager: CrateManager,
        crateEligibilityService: CrateEligibilityService,
        keyService: KeyService,
        openService: OpenService,
        storageService: StorageService,
        anchorService: AnchorService,
        displayService: DisplayService,
        guiService: GuiService,
        pluginScope: CoroutineScope,
        messageService: MessageService,
        economyService: SlateEconomyService,
        claimService: ClaimService,
        playerCrateQueryService: PlayerCrateQueryService,
    ) {
        this.plugin = plugin
        this.configManager = configManager
        this.crateManager = crateManager
        this.crateEligibilityService = crateEligibilityService
        this.keyService = keyService
        this.openService = openService
        this.storageService = storageService
        this.anchorService = anchorService
        this.displayService = displayService
        this.guiService = guiService
        this.pluginScope = pluginScope
        this.messageService = messageService
        this.economyService = economyService
        this.claimService = claimService
        this.playerCrateQueryService = playerCrateQueryService
    }

    fun isKey(item: ItemStack): Boolean = requireReady(keyService).isKey(item)
    fun getCrateForKey(item: ItemStack): CrateDefinition? = requireReady(keyService).getCrateForKey(item)
    suspend fun getVirtualKeys(uuid: UUID, crateId: String): Int = requireReady(keyService).getVirtualKeys(uuid, crateId)

    fun configManager(): ConfigManager? = configManager
    fun crateManager(): CrateManager? = crateManager
    fun crateEligibilityService(): CrateEligibilityService? = crateEligibilityService
    fun keyService(): KeyService? = keyService
    fun openService(): OpenService? = openService
    fun storageService(): StorageService? = storageService
    fun anchorService(): AnchorService? = anchorService
    fun displayService(): DisplayService? = displayService
    fun guiService(): GuiService? = guiService
    fun pluginScope(): CoroutineScope? = pluginScope
    fun plugin(): SlatePlugin? = plugin
    fun messageService(): MessageService? = messageService
    fun economyService(): SlateEconomyService? = economyService
    fun claimService(): ClaimService? = claimService
    fun playerCrateQueryService(): PlayerCrateQueryService? = playerCrateQueryService
    fun scheduler(): PlatformScheduler? = plugin?.platformScheduler

    private fun <T> requireReady(value: T?): T =
        value ?: throw IllegalStateException("Slate not enabled")
}
