package so.alaz.slate.gui.animation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Sound
import org.bukkit.entity.Player
import so.alaz.slate.crate.Prize
import so.alaz.slate.gui.GuiClickContext
import so.alaz.slate.gui.GuiSession
import so.alaz.slate.gui.GuiSlot
import so.alaz.slate.gui.SlateGui
import so.alaz.slate.scheduler.CancellableTask
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

    override suspend fun onClick(context: GuiClickContext, session: GuiSession, slot: GuiSlot?) {
        // Timer-driven completion only; clicks do not alter outcome timing.
    }

    suspend fun play(
        player: Player,
        session: GuiSession,
        scheduler: PlatformScheduler,
    ) {
        suspendCancellableCoroutine { continuation ->
            var tick = 0
            var revealPlayed = false
            var completed = false
            var task: CancellableTask? = null

            fun finish() {
                if (completed) return
                completed = true
                stateBySession[session.sessionId] = AnimationState.COMPLETE
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            fun endAnimation() {
                val activeTask = task
                if (activeTask != null && !activeTask.isCancelled) {
                    activeTask.cancel()
                }
                finish()
            }

            fun renderFinalFrame() {
                stateBySession[session.sessionId] = AnimationState.COMPLETE
                val frame = template.frame(template.totalTicks, prize, decoys)
                frame.slotItems.forEach { (slotIndex, item) ->
                    session.inventory.setItem(slotIndex, item)
                }
            }

            task = scheduler.runRepeatingForEntity(
                entity = player,
                initialDelayTicks = 1,
                periodTicks = 1,
                retiredCallback = { finish() },
            ) {
                if (completed) {
                    return@runRepeatingForEntity
                }
                if (!player.isOnline) {
                    endAnimation()
                    return@runRepeatingForEntity
                }

                if (forceCompleteRequests.remove(session.sessionId) == true) {
                    renderFinalFrame()
                    revealPlayed = playRevealSound(player, revealPlayed)
                    endAnimation()
                    return@runRepeatingForEntity
                }

                if (tick < template.totalTicks && template.shouldRenderOnTick(tick)) {
                    stateBySession[session.sessionId] = stateForTick(tick)
                    val frame = template.frame(tick, prize, decoys)
                    frame.slotItems.forEach { (slotIndex, item) ->
                        session.inventory.setItem(slotIndex, item)
                    }
                    revealPlayed = playTickSound(player, frame, tick, revealPlayed)
                }

                tick++
                if (tick >= template.totalTicks) {
                    renderFinalFrame()
                    revealPlayed = playRevealSound(player, revealPlayed)
                    endAnimation()
                }
            }

            continuation.invokeOnCancellation {
                val activeTask = task
                if (activeTask != null && !activeTask.isCancelled) {
                    activeTask.cancel()
                }
            }
        }
    }

    fun requestImmediateCompletion(sessionId: UUID) {
        forceCompleteRequests[sessionId] = true
    }

    fun isComplete(sessionId: UUID): Boolean = stateBySession[sessionId] == AnimationState.COMPLETE

    private fun playTickSound(player: Player, frame: AnimationFrame, tick: Int, revealPlayed: Boolean): Boolean {
        val state = stateForTick(tick)
        val shouldReveal = state == AnimationState.REVEALING
        val soundName = when {
            frame.sound != null -> frame.sound
            shouldReveal && !revealPlayed -> revealSound
            shouldReveal -> null
            else -> tickSound
        }
        playSoundDirect(player, soundName)
        return revealPlayed || (shouldReveal && revealSound != null)
    }

    private fun playRevealSound(player: Player, revealPlayed: Boolean): Boolean {
        if (revealPlayed) return true
        playSoundDirect(player, revealSound)
        return revealSound != null
    }

    private fun playSoundDirect(player: Player, raw: String?) {
        if (raw.isNullOrBlank()) return
        val sound = runCatching { Sound.valueOf(raw) }.getOrNull() ?: return
        player.playSound(player.location, sound, 1.0f, 1.0f)
    }

    private fun stateForTick(tick: Int): AnimationState = when {
        tick < template.totalTicks / 2 -> AnimationState.SPINNING
        tick < template.totalTicks - 5 -> AnimationState.SLOWING
        tick < template.totalTicks -> AnimationState.REVEALING
        else -> AnimationState.COMPLETE
    }
}
