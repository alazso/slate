package so.alaz.slate.listener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Event.Result.DENY
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import so.alaz.slate.api.SlateAPI
import so.alaz.slate.command.SlatePermissions
import so.alaz.slate.crate.anchor.AnchorService
import so.alaz.slate.event.CrateInteractEvent
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
) : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val anchoredCrateId = anchorService.crateIdAtCached(event.block.location) ?: return
        if (
            event.player.hasPermission(SlatePermissions.ADMIN_ANCHOR_SET) ||
            event.player.hasPermission(SlatePermissions.ADMIN_ANCHOR_REMOVE)
        ) {
            return
        }

        event.isCancelled = true
        CommandMessages.chat(event.player, MessageKeys.ANCHOR_BREAK_DENIED, mapOf("crate" to anchoredCrateId))
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return

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
                when (val result = openService.openFromInteraction(player, anchoredCrate, item)) {
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
}
