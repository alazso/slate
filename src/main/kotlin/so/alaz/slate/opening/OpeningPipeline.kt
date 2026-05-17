package so.alaz.slate.opening

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.event.Event
import so.alaz.slate.crate.CrateEligibilityService
import so.alaz.slate.crate.CrateDefinition
import so.alaz.slate.event.CrateOpenPostEvent
import so.alaz.slate.event.CrateOpenPreEvent
import so.alaz.slate.event.CratePrizeWinEvent
import so.alaz.slate.event.KeyConsumeEvent
import so.alaz.slate.key.KeyService
import so.alaz.slate.key.KeyType
import so.alaz.slate.prize.PityTracker
import so.alaz.slate.prize.PrizeEngine
import so.alaz.slate.probability.ProbabilityVariableService
import so.alaz.slate.scheduler.PlatformScheduler
import so.alaz.slate.storage.OpenOperationCreate
import so.alaz.slate.storage.OpenOperationState
import so.alaz.slate.storage.StorageService
import so.alaz.slate.util.ItemUtil
import so.alaz.slate.util.LogUtil
import java.util.UUID

class OpeningPipeline(
    private val keyService: KeyService,
    private val prizeEngine: PrizeEngine,
    private val pityTracker: PityTracker,
    private val storageService: StorageService,
    private val scheduler: PlatformScheduler,
    private val crateEligibilityService: CrateEligibilityService,
) {
    suspend fun execute(
        player: org.bukkit.entity.Player,
        crate: CrateDefinition,
        keyType: KeyType,
        requestId: String? = null,
    ): OpeningResult {
        val operation = storageService.createOrGetOpenOperation(
            OpenOperationCreate(
                operationId = requestId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                requestId = requestId,
                uuid = player.uniqueId,
                crateId = crate.id,
                keyType = keyType,
            ),
        )
        when (operation.state) {
            OpenOperationState.DELIVERED -> return OpeningResult.Success(operation.prizeId ?: "__delivered__")
            OpenOperationState.RECOVERABLE -> return OpeningResult.Failure(FailureReason.DELIVERY_FAILED)
            OpenOperationState.FAILED -> return OpeningResult.Failure(FailureReason.CANCELLED_BY_EVENT)
            else -> Unit
        }

        val session = OpeningSession(player, crate, keyType)
        val eligibility = crateEligibilityService.canOpen(player, crate)
        if (!eligibility.allowed) {
            failOperation(operation.operationId, "eligibility failed: ${eligibility.failureReason}")
            return OpeningResult.Failure(eligibility.failureReason ?: FailureReason.CANCELLED_BY_EVENT)
        }

        val cooldown = storageService.getCooldown(player.uniqueId, crate.id)
        if (cooldown != null && crate.cooldown.isPositive()) {
            val elapsedMs = System.currentTimeMillis() - cooldown.toEpochMilli()
            if (elapsedMs < crate.cooldown.inWholeMilliseconds) {
                failOperation(operation.operationId, "cooldown active")
                return OpeningResult.Failure(FailureReason.ON_COOLDOWN)
            }
        }

        if (crate.maxPulls != -1 && storageService.countOpens(player.uniqueId, crate.id) >= crate.maxPulls) {
            failOperation(operation.operationId, "max pulls reached")
            return OpeningResult.Failure(FailureReason.MAX_PULLS_REACHED)
        }

        val availableKeys = keyService.countKeys(player, crate, keyType)
        if (availableKeys < crate.requiredKeys) {
            val reason = if (availableKeys <= 0) FailureReason.NO_KEY else FailureReason.INSUFFICIENT_KEYS
            failOperation(operation.operationId, "insufficient keys: required=${crate.requiredKeys}, available=$availableKeys")
            return OpeningResult.Failure(
                reason = reason,
                requiredKeys = crate.requiredKeys,
                availableKeys = availableKeys,
            )
        }

        val preEvent = CrateOpenPreEvent(player, crate, keyType)
        fireOnMain(preEvent)
        if (preEvent.isCancelled) {
            failOperation(operation.operationId, "cancelled by event")
            return OpeningResult.Failure(FailureReason.CANCELLED_BY_EVENT)
        }

        if (!keyService.consumeKey(player, crate, keyType)) {
            failOperation(operation.operationId, "key consume failed")
            val remainingKeys = keyService.countKeys(player, crate, keyType)
            return OpeningResult.Failure(
                reason = FailureReason.INSUFFICIENT_KEYS,
                requiredKeys = crate.requiredKeys,
                availableKeys = remainingKeys,
            )
        }
        session.keyConsumed = true
        storageService.updateOpenOperation(operation.operationId, OpenOperationState.KEY_CONSUMED)
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.KEY_CONSUMED, "key consumed")
        fireOnMain(KeyConsumeEvent(player, crate, keyType))

        val pityCount = pityTracker.increment(player.uniqueId, crate.id)
        val prize = prizeEngine.selectPrize(
            crate = crate,
            pityTier = crate.pityConfig?.guaranteedTier?.takeIf { crate.pityConfig.enabled && pityCount >= crate.pityConfig.opensRequired },
            pityCount = pityCount,
        )
        session.selectedPrize = prize
        storageService.updateOpenOperation(
            operation.operationId,
            OpenOperationState.PRIZE_SELECTED,
            prizeId = prize?.id,
        )
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.PRIZE_SELECTED, "prize selected")

        if (prize != null && crate.pityConfig?.enabled == true && prize.tier == crate.pityConfig.guaranteedTier) {
            pityTracker.reset(player.uniqueId, crate.id)
        }

        if (prize == null) {
            storageService.logOpen(player.uniqueId, crate.id, "__empty__", "Empty Roll", keyType)
            storageService.setCooldown(player.uniqueId, crate.id, java.time.Instant.now())
            storageService.updateOpenOperation(operation.operationId, OpenOperationState.DELIVERED, prizeId = "__empty__")
            storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.DELIVERED, "empty roll delivered")
            fireOnMain(CrateOpenPostEvent(player, crate))
            return OpeningResult.Success("__empty__")
        }

        val animationConfig = crate.animation
        if (animationConfig?.enabled == true) {
            val guiService = so.alaz.slate.api.SlateAPI.guiService()
            if (guiService != null) {
                val decoys = crate.prizes
                    .filter { it.id != prize.id }
                    .shuffled()
                    .take(animationConfig.decoyCount.coerceAtLeast(0))
                runCatching {
                    guiService.playAnimation(player, animationConfig, prize, decoys)
                }.onFailure {
                    LogUtil.warn("Animation phase failed for crate ${crate.id}: ${it.message}")
                }
            }
        }

        val deliveryResult = runCatching { deliverPrize(session) }
        if (deliveryResult.isFailure) {
            storageService.addPendingPrize(player.uniqueId, crate.id, prize.id)
            LogUtil.error("Failed to deliver prize; queued pending", deliveryResult.exceptionOrNull())
            storageService.updateOpenOperation(
                operation.operationId,
                OpenOperationState.RECOVERABLE,
                prizeId = prize.id,
                errorMessage = deliveryResult.exceptionOrNull()?.message,
            )
            storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.RECOVERABLE, "delivery failed, queued pending")
            return OpeningResult.Failure(FailureReason.DELIVERY_FAILED)
        }

        storageService.logOpen(player.uniqueId, crate.id, prize.id, prize.name, keyType)
        storageService.setCooldown(player.uniqueId, crate.id, java.time.Instant.now())
        storageService.updateOpenOperation(operation.operationId, OpenOperationState.DELIVERED, prizeId = prize.id)
        storageService.appendOpenOperationStep(operation.operationId, OpenOperationState.DELIVERED, "prize delivered")
        fireOnMain(CrateOpenPostEvent(player, crate))
        fireOnMain(CratePrizeWinEvent(player, crate, prize))
        return OpeningResult.Success(prize.id)
    }

    private suspend fun deliverPrize(session: OpeningSession) {
        val prize = requireNotNull(session.selectedPrize) { "Prize must be selected before delivery" }
        runOnMain {
            val rewardStacks = prize.items.flatMap { prizeItem ->
                ItemUtil.expandPrizeItem(prizeItem)
                    ?: throw IllegalStateException("Invalid prize item '${prizeItem.material}' in prize '${prize.id}'")
            }
            if (rewardStacks.isNotEmpty()) {
                val overflow = session.player.inventory.addItem(*rewardStacks.toTypedArray())
                if (overflow.isNotEmpty()) {
                    throw IllegalStateException("Inventory full while delivering prize '${prize.id}'")
                }
            }
            for (cmd in prize.commands) {
                val prepared = cmd.replace("%player%", session.player.name)
                val ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared)
                if (!ok) {
                    throw IllegalStateException("Command delivery failed for '$prepared'")
                }
            }
            for (message in prize.messages) {
                val variables = ProbabilityVariableService.crateVariables(session.crate.id) +
                    ProbabilityVariableService.rewardVariables(session.crate.id, prize.id) +
                    (prize.tier?.let { ProbabilityVariableService.tierVariables(session.crate.id, it) } ?: emptyMap())
                val rendered = ProbabilityVariableService.render(message, variables)
                val messageService = so.alaz.slate.api.SlateAPI.messageService()
                if (messageService != null) {
                    messageService.chatRaw(session.player, rendered)
                }
            }
        }
    }

    private suspend fun fireOnMain(event: Event) = runOnMain {
        Bukkit.getPluginManager().callEvent(event)
    }

    private suspend fun runOnMain(task: () -> Unit) = suspendCancellableCoroutine<Unit> { cont ->
        scheduler.runGlobal {
            runCatching(task)
                .onSuccess { cont.resume(Unit) }
                .onFailure { cont.resumeWithException(it) }
        }
    }

    private suspend fun failOperation(operationId: String, message: String) {
        storageService.updateOpenOperation(
            operationId = operationId,
            state = OpenOperationState.FAILED,
            errorMessage = message,
        )
        storageService.appendOpenOperationStep(operationId, OpenOperationState.FAILED, message)
    }
}

sealed class OpeningResult {
    data class Success(val prizeId: String) : OpeningResult()
    data class Failure(
        val reason: FailureReason,
        val requiredKeys: Int? = null,
        val availableKeys: Int? = null,
    ) : OpeningResult()
}

enum class FailureReason {
    NO_KEY,
    INSUFFICIENT_KEYS,
    ON_COOLDOWN,
    MAX_PULLS_REACHED,
    NOT_ALLOWED_WORLD,
    CRATE_DISABLED,
    CRATE_ARCHIVED,
    CRATE_OUT_OF_SEASON,
    MISSING_PERMISSION,
    REGION_RESTRICTED,
    WORLDGUARD_UNAVAILABLE,
    CANCELLED_BY_EVENT,
    DELIVERY_FAILED,
}
