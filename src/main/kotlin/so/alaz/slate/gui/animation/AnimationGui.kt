package so.alaz.slate.gui.animation

import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Sound
import org.bukkit.entity.Player
import so.alaz.slate.crate.Prize
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.scheduler.PlatformScheduler

enum class AnimationState {
    IDLE,
    SPINNING,
    SLOWING,
    REVEALING,
    COMPLETE,
}

class AnimationGui(
    private val template: AnimationTemplate,
    private val prize: Prize,
    private val decoys: List<Prize>,
    private val tickSound: String? = null,
    private val revealSound: String? = null,
) : SlateGui("Opening...", 3) {
    private val stateBySession = ConcurrentHashMap<UUID, AnimationState>()
    private val forceCompleteRequests = ConcurrentHashMap<UUID, Boolean>()

    override suspend fun onAfterOpen(player: Player, session: GuiSession) {
        stateBySession[session.sessionId] = AnimationState.IDLE
    }

    override suspend fun onClose(player: Player, session: GuiSession) {
        stateBySession.remove(session.sessionId)
        forceCompleteRequests.remove(session.sessionId)
    }

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: so.alaz.slate.gui.GuiSlot?) {
        // Timer-driven completion only; clicks do not alter outcome timing.
    }

    suspend fun play(
        player: Player,
        session: GuiSession,
        scheduler: PlatformScheduler,
    ) {
        val step = template.tickInterval.coerceAtLeast(1)
        var tick = 0
        var revealPlayed = false
        while (tick <= template.totalTicks) {
            if (!player.isOnline) break
            if (forceCompleteRequests[session.sessionId] == true) break
            val state = when {
                tick < template.totalTicks / 2 -> AnimationState.SPINNING
                tick < template.totalTicks - 5 -> AnimationState.SLOWING
                tick < template.totalTicks -> AnimationState.REVEALING
                else -> AnimationState.COMPLETE
            }
            stateBySession[session.sessionId] = state
            val frame = template.frame(tick, prize, decoys)
            frame.slotItems.forEach { (slotIndex, item) ->
                runGlobal(scheduler) {
                    session.inventory.setItem(slotIndex, item)
                }
            }
            val soundName = when {
                frame.sound != null -> frame.sound
                state == AnimationState.REVEALING || state == AnimationState.COMPLETE -> {
                    if (!revealPlayed) {
                        revealPlayed = true
                        revealSound
                    } else {
                        null
                    }
                }
                else -> tickSound
            }
            playSoundIfPresent(player, scheduler, soundName)
            if (state == AnimationState.COMPLETE) break
            waitTicks(scheduler, step.toLong())
            tick += step
        }
        stateBySession[session.sessionId] = AnimationState.COMPLETE
        if (!revealPlayed) {
            playSoundIfPresent(player, scheduler, revealSound)
        }
    }

    fun requestImmediateCompletion(sessionId: UUID) {
        forceCompleteRequests[sessionId] = true
    }

    fun isComplete(sessionId: UUID): Boolean = stateBySession[sessionId] == AnimationState.COMPLETE

    private suspend fun playSoundIfPresent(player: Player, scheduler: PlatformScheduler, raw: String?) {
        if (raw.isNullOrBlank()) return
        val sound = runCatching { Sound.valueOf(raw) }.getOrNull() ?: return
        runGlobal(scheduler) {
            player.playSound(player.location, sound, 1.0f, 1.0f)
        }
    }

    private suspend fun waitTicks(scheduler: PlatformScheduler, ticks: Long) {
        val delay = Duration.ofMillis((ticks.coerceAtLeast(1) * 50L))
        suspendCancellableCoroutine<Unit> { continuation ->
            scheduler.runGlobalDelayed(delay) {
                continuation.resume(Unit)
            }
        }
    }

    private suspend fun <T> runGlobal(scheduler: PlatformScheduler, block: () -> T): T =
        suspendCancellableCoroutine { continuation ->
            scheduler.runGlobal {
                runCatching { block() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }
        }
}

