package so.alaz.slate.gui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.plugin.Plugin
import so.alaz.slate.config.AnimationCloseSafeguard
import so.alaz.slate.crate.CrateAnimationConfig
import so.alaz.slate.crate.Prize
import so.alaz.slate.config.PluginConfig
import so.alaz.slate.gui.animation.AgencyPickAnimation
import so.alaz.slate.gui.animation.AnimationGui
import so.alaz.slate.gui.animation.AnimationTemplate
import so.alaz.slate.gui.animation.CsgoAnimation
import so.alaz.slate.gui.animation.SlotMachineAnimation
import so.alaz.slate.scheduler.PlatformScheduler

class GuiService(
    private val plugin: Plugin,
    private val scheduler: PlatformScheduler,
    private val pluginScope: CoroutineScope,
    private val pluginConfigProvider: () -> PluginConfig,
) : Listener {
    private val sessionsByPlayer = ConcurrentHashMap<UUID, GuiSession>()
    private val sessionsByHolderId = ConcurrentHashMap<UUID, GuiSession>()
    private val openMutexByPlayer = ConcurrentHashMap<UUID, Mutex>()
    private val clickInFlightByPlayer = ConcurrentHashMap<UUID, AtomicBoolean>()
    private val shuttingDown = AtomicBoolean(false)

    suspend fun openGui(player: Player, gui: SlateGui) {
        val mutex = openMutexByPlayer.computeIfAbsent(player.uniqueId) { Mutex() }
        mutex.withLock {
            val existing = sessionsByPlayer[player.uniqueId]
            if (existing != null) {
                closeSession(player, existing)
            }
            val session = gui.open(player, scheduler)
            val holder = session.inventory.holder as? GuiInventoryHolder
            sessionsByPlayer[player.uniqueId] = session
            if (holder != null) {
                sessionsByHolderId[holder.holderId] = session
            }
        }
    }

    suspend fun playAnimation(
        player: Player,
        animationConfig: CrateAnimationConfig,
        prize: Prize,
        decoys: List<Prize>,
    ) {
        val template = templateFor(animationConfig)
        val gui = AnimationGui(
            template = template,
            prize = prize,
            decoys = decoys,
            tickSound = animationConfig.soundTick,
            revealSound = animationConfig.soundReveal,
        )
        openGui(player, gui)
        val session = sessionsByPlayer[player.uniqueId] ?: return
        val timeoutSeconds = pluginConfigProvider().gui.animationTimeoutSeconds.coerceAtLeast(1)
        runCatching {
            withTimeout(timeoutSeconds.seconds) {
                gui.play(player, session, scheduler)
            }
        }
        scheduler.runGlobalDelayed(Duration.ofSeconds(5)) {
            val activeSession = sessionsByPlayer[player.uniqueId]
            if (activeSession?.sessionId != session.sessionId) return@runGlobalDelayed
            val activeAnimation = activeSession.gui as? AnimationGui ?: return@runGlobalDelayed
            if (!activeAnimation.isComplete(activeSession.sessionId)) return@runGlobalDelayed
            if (player.openInventory.topInventory.holder is GuiInventoryHolder) {
                player.closeInventory()
            }
        }
    }

    fun register() {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun closeAll() {
        shuttingDown.set(true)
        sessionsByPlayer.values.toList().forEach { session ->
            val player = plugin.server.getPlayer(session.playerId) ?: return@forEach
            if (plugin.isEnabled) {
                scheduler.runGlobal {
                    player.closeInventory()
                }
            } else {
                player.closeInventory()
            }
        }
        sessionsByPlayer.clear()
        sessionsByHolderId.clear()
        openMutexByPlayer.clear()
        clickInFlightByPlayer.clear()
        shuttingDown.set(false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? GuiInventoryHolder ?: return
        val session = sessionsByHolderId[holder.holderId] ?: return
        val player = event.whoClicked as? Player ?: return
        if (!session.isOwner(player)) return
        if (!session.passesDebounce(pluginConfigProvider().gui.clickCooldownMs)) {
            event.isCancelled = true
            return
        }
        if (event.rawSlot !in 0 until event.view.topInventory.size) {
            event.isCancelled = true
            return
        }
        val clickGuard = clickInFlightByPlayer.computeIfAbsent(player.uniqueId) { AtomicBoolean(false) }
        if (!clickGuard.compareAndSet(false, true)) {
            event.isCancelled = true
            return
        }
        event.isCancelled = true
        val slot = session.gui.getSlot(event.rawSlot)
        pluginScope.launch {
            runCatching {
                val context = GuiClickContext(
                    player = player,
                    session = session,
                    slotIndex = event.rawSlot,
                    clickType = event.click,
                )
                session.gui.onClick(context, session, slot)
                if (session.closeRequested) {
                    session.closeRequested = false
                    scheduler.runForPlayerEntity(player) {
                        player.closeInventory()
                    }
                    return@runCatching
                }
                if (session.gui is AnimationGui) {
                    return@runCatching
                }
                session.gui.refresh(player, scheduler, session.inventory)
            }.onFailure {
                plugin.logger.warning("GUI click handling failed for ${player.name}: ${it.message}")
            }.also {
                clickGuard.set(false)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? GuiInventoryHolder ?: return
        if (sessionsByHolderId.containsKey(holder.holderId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuiInventoryHolder ?: return
        val session = sessionsByHolderId[holder.holderId] ?: return
        val player = event.player as? Player ?: return
        val animationGui = session.gui as? AnimationGui
        val isAnimationActive = animationGui != null && !animationGui.isComplete(session.sessionId)
        if (!shuttingDown.get() && isAnimationActive) {
            when (pluginConfigProvider().gui.animationCloseSafeguard) {
                AnimationCloseSafeguard.COMPLETE_ON_CLOSE -> {
                    animationGui.requestImmediateCompletion(session.sessionId)
                }

                AnimationCloseSafeguard.PREVENT_CLOSE_UNTIL_COMPLETE -> {
                    scheduler.runGlobal {
                        player.openInventory(session.inventory)
                    }
                    return
                }
            }
        }
        sessionsByHolderId.remove(holder.holderId)
        sessionsByPlayer.remove(session.playerId)
        clickInFlightByPlayer.remove(session.playerId)
        pluginScope.launch {
            runCatching {
                session.gui.onClose(player, session)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (sessionsByPlayer.containsKey(player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (sessionsByPlayer.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (sessionsByPlayer.containsKey(event.player.uniqueId)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onQuit(event: PlayerQuitEvent) {
        val session = sessionsByPlayer.remove(event.player.uniqueId) ?: return
        val holder = session.inventory.holder as? GuiInventoryHolder
        if (holder != null) {
            sessionsByHolderId.remove(holder.holderId)
        }
        clickInFlightByPlayer.remove(event.player.uniqueId)
        openMutexByPlayer.remove(event.player.uniqueId)
    }

    private suspend fun closeSession(player: Player, session: GuiSession) {
        scheduler.runGlobal { player.closeInventory() }
        sessionsByPlayer.remove(session.playerId)
        clickInFlightByPlayer.remove(session.playerId)
        val holder = session.inventory.holder as? GuiInventoryHolder
        if (holder != null) {
            sessionsByHolderId.remove(holder.holderId)
        }
    }

    private fun templateFor(config: CrateAnimationConfig): AnimationTemplate = when (config.template.lowercase()) {
        "slot-machine" -> SlotMachineAnimation(
            totalTicks = config.durationTicks,
            tickInterval = config.tickInterval,
            spinSpeedMultiplier = config.spinSpeedMultiplier,
            revealTailTicks = config.revealTailTicks,
        )

        "agency-pick" -> AgencyPickAnimation(
            totalTicks = config.durationTicks,
            tickInterval = config.tickInterval,
            spinSpeedMultiplier = config.spinSpeedMultiplier,
            revealTailTicks = config.revealTailTicks,
        )

        else -> CsgoAnimation(
            totalTicks = config.durationTicks,
            tickInterval = config.tickInterval,
            spinSpeedMultiplier = config.spinSpeedMultiplier,
            revealTailTicks = config.revealTailTicks,
        )
    }
}

