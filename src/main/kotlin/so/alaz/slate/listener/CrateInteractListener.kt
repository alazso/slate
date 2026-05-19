package so.alaz.slate.listener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Event.Result.DENY
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.config.PluginConfig
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.crate.CrateLeftClickAction
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.event.CrateInteractEvent
import so.alaz.slate.gui.preview.PreviewGui
import so.alaz.slate.key.KeyService
import so.alaz.slate.message.MessageKeys
import so.alaz.slate.opening.OpenService
import so.alaz.slate.opening.OpeningResult
import so.alaz.slate.command.sub.CommandMessages
import so.alaz.slate.scheduler.PlatformScheduler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CrateInteractListener(
    private val pluginScope: CoroutineScope,
    private val keyService: KeyService,
    private val openService: OpenService,
    private val anchorService: AnchorService,
    private val scheduler: PlatformScheduler,
    private val configProvider: () -> PluginConfig = { PluginConfig() },
) : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val location = event.block.location
        val anchoredCrateId = anchorService.crateIdAtCached(location) ?: return
        if (canManageAnchors(event.player).not()) {
            event.isCancelled = true
            CommandMessages.chat(event.player, MessageKeys.ANCHOR_BREAK_DENIED, mapOf("crate" to anchoredCrateId))
            return
        }

        val breakConfig = configProvider().anchors
        if (breakConfig.breakConfirmEnabled && consumeBreakConfirmation(event.player, location).not()) {
            event.isCancelled = true
            CommandMessages.chat(
                event.player,
                MessageKeys.ANCHOR_BREAK_CONFIRM_REQUIRED,
                mapOf("crate" to anchoredCrateId, "seconds" to breakConfig.breakConfirmWindowSeconds),
            )
            return
        }

        pluginScope.launch {
            removeAnchorAfterBreak(event.player, location, anchoredCrateId)
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (
            event.action != Action.RIGHT_CLICK_BLOCK &&
            event.action != Action.RIGHT_CLICK_AIR &&
            event.action != Action.LEFT_CLICK_BLOCK
        ) {
            return
        }

        val anchoredCrateId = event.clickedBlock?.location?.let { anchorService.crateIdAtCached(it) }
        if (anchoredCrateId != null) {
            event.isCancelled = true
            event.setUseInteractedBlock(DENY)
            event.setUseItemInHand(DENY)
        }

        val item = event.item
        pluginScope.launch {
            val player = event.player
            if (anchoredCrateId != null) {
                val anchoredCrate = SlateAPI.crateManager()?.byId(anchoredCrateId)
                if (anchoredCrate == null) {
                    runForPlayer(player) {
                        CommandMessages.chat(player, MessageKeys.ANCHOR_UNKNOWN_TARGET, mapOf("crate" to anchoredCrateId))
                    }
                    return@launch
                }
                when (event.action) {
                    Action.RIGHT_CLICK_BLOCK,
                    Action.RIGHT_CLICK_AIR,
                    -> openAnchoredCrate(player, anchoredCrate, item)

                    Action.LEFT_CLICK_BLOCK -> {
                        if (anchoredCrate.interaction.leftClickAction == CrateLeftClickAction.OPEN_PREVIEW) {
                            openPreviewForLeftClick(player, anchoredCrate)
                        }
                    }

                    else -> Unit
                }
                return@launch
            }

            if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) {
                return@launch
            }
            val crate = item?.let { keyService.getCrateForKey(it) } ?: return@launch
            val interactEvent = CrateInteractEvent(player, crate)
            runForPlayer(player) {
                player.server.pluginManager.callEvent(interactEvent)
            }
            if (interactEvent.isCancelled) return@launch
            when (val result = openService.openFromInteraction(player, crate, item)) {
                is OpeningResult.Success -> Unit
                is OpeningResult.Failure -> runForPlayer(player) {
                    CommandMessages.chat(
                        player,
                        MessageKeys.OPEN_FAILED_REASON,
                        mapOf("reason" to CommandMessages.openFailureReason(result)),
                    )
                }
            }
        }
    }

    private suspend fun runForPlayer(player: org.bukkit.entity.Player, block: () -> Unit) {
        suspendCancellableCoroutine { continuation ->
            scheduler.runForPlayerEntity(player) {
                runCatching(block)
                    .onSuccess { continuation.resume(Unit) }
                    .onFailure { continuation.resumeWithException(it) }
            }
        }
    }

    private suspend fun openAnchoredCrate(player: Player, crate: CrateDefinition, item: org.bukkit.inventory.ItemStack?) {
        when (val result = openService.openFromInteraction(player, crate, item)) {
            is OpeningResult.Success -> Unit
            is OpeningResult.Failure -> {
                runForPlayer(player) {
                    CommandMessages.chat(
                        player,
                        MessageKeys.OPEN_FAILED_REASON,
                        mapOf("reason" to CommandMessages.openFailureReason(result)),
                    )
                }
            }
        }
    }

    private suspend fun openPreviewForLeftClick(player: Player, crate: CrateDefinition) {
        if (
            !player.hasPermission(SlatePermissions.PLAYER_PREVIEW) ||
            !player.hasPermission("${SlatePermissions.PREVIEW_CRATE_PREFIX}${crate.id}")
        ) {
            runForPlayer(player) {
                CommandMessages.chat(player, MessageKeys.PREVIEW_FORBIDDEN, mapOf("crate" to crate.id))
            }
            return
        }
        val eligibilityService = SlateAPI.crateEligibilityService()
        if (eligibilityService == null) {
            runForPlayer(player) {
                CommandMessages.notInitialized(player)
            }
            return
        }
        val previewEligibility = eligibilityService.canPreview(player, crate)
        if (!previewEligibility.allowed) {
            runForPlayer(player) {
                CommandMessages.chat(
                    player,
                    MessageKeys.OPEN_FAILED_REASON,
                    mapOf(
                        "reason" to CommandMessages.openFailureReason(
                            previewEligibility.failureReason ?: so.alaz.slate.opening.FailureReason.CANCELLED_BY_EVENT,
                        ),
                    ),
                )
            }
            return
        }
        val guiService = SlateAPI.guiService()
        if (guiService != null && crate.preview?.enabled == true) {
            val opened = runCatching {
                guiService.openGui(player, PreviewGui(crate))
            }.isSuccess
            if (opened) return
        }
        runForPlayer(player) {
            CommandMessages.chat(player, MessageKeys.COMMON_NOT_IMPLEMENTED, mapOf("feature" to "left-click preview GUI"))
        }
    }

    private fun canManageAnchors(player: Player): Boolean =
        player.hasPermission(SlatePermissions.ADMIN_ANCHOR_SET) ||
            player.hasPermission(SlatePermissions.ADMIN_ANCHOR_REMOVE)

    private fun consumeBreakConfirmation(player: Player, location: Location): Boolean {
        val now = System.currentTimeMillis()
        breakConfirmations.entries.removeIf { it.value <= now }
        val key = BreakConfirmKey(
            playerId = player.uniqueId,
            world = location.world?.key?.asString().orEmpty(),
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
        )
        val expiry = breakConfirmations[key]
        if (expiry != null && expiry > now) {
            breakConfirmations.remove(key)
            return true
        }
        val windowMillis = configProvider().anchors.breakConfirmWindowSeconds.coerceAtLeast(1L) * 1000L
        breakConfirmations[key] = now + windowMillis
        return false
    }

    private suspend fun removeAnchorAfterBreak(player: Player, location: Location, crateId: String) {
        val removed = anchorService.removeAnchor(location)
        if (!removed) return
        SlateAPI.displayService()?.removeDisplayGroupAt(
            ownerType = "crate",
            ownerId = crateId,
            location = location.clone().add(0.5, 0.5, 0.5),
        )
        SlateAPI.displayService()?.reloadDisplays()
        runForPlayer(player) {
            CommandMessages.chat(player, MessageKeys.ANCHOR_BREAK_REMOVED, mapOf("crate" to crateId))
        }
    }

    private data class BreakConfirmKey(
        val playerId: UUID,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
    )

    private val breakConfirmations = ConcurrentHashMap<BreakConfirmKey, Long>()
}
